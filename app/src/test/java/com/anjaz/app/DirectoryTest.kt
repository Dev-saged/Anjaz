package com.anjaz.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anjaz.app.data.Clock
import com.anjaz.app.data.DirectoryRepository
import com.anjaz.app.data.db.AnjazDatabase
import com.anjaz.app.data.db.CampEntity
import com.anjaz.app.data.db.RegionEntity
import com.anjaz.app.data.db.SquareEntity
import com.anjaz.app.data.db.TaskEntity
import com.anjaz.app.data.db.UserEntity
import com.anjaz.app.domain.Outcome
import com.anjaz.app.domain.Profile
import com.anjaz.app.sync.Notifier
import com.anjaz.app.sync.RemoteStore
import com.anjaz.app.sync.SyncEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class DirRemote : RemoteStore {
    override val enabled = true
    val log = mutableListOf<String>()
    override suspend fun createTask(task: TaskEntity) = Unit
    override suspend fun updateTaskStatus(taskId: String, status: String, updatedAt: Long) = Unit
    override suspend fun appendEvent(event: com.anjaz.app.data.db.TaskEventEntity) = Unit
    override fun tasks(profile: Profile): Flow<List<TaskEntity>> = emptyFlow()
    override fun users(profile: Profile): Flow<List<UserEntity>> = emptyFlow()
    override suspend fun createRegion(region: RegionEntity) { log += "region:${region.name}" }
    override suspend fun createSquare(square: SquareEntity) { log += "square:${square.name}@${square.regionId}" }
    override suspend fun createCamp(camp: CampEntity) { log += "camp:${camp.name}@${camp.regionId}/${camp.squareId}" }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DirectoryTest {
    private lateinit var db: AnjazDatabase
    private lateinit var remote: DirRemote
    private lateinit var engine: SyncEngine
    private lateinit var repo: DirectoryRepository
    private var now = 9_000L
    private val clock = Clock { now++ }
    private val central = Profile("c", "c", "المركز", "CENTRAL")
    private val regionMgr = Profile("r", "r", "مدير", "REGION_MANAGER", "R1")
    private val squareMgr = Profile("q", "q", "مدير مربع", "SQUARE_MANAGER", "R1", "S1")
    private val staff = Profile("s", "s", "موظف", "FIELD_STAFF", "R1", "S1")

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AnjazDatabase::class.java).build()
        remote = DirRemote()
        engine = SyncEngine(db, remote, Notifier { _, _ -> }, clock)
        repo = DirectoryRepository(db, clock)
    }

    @After fun tearDown() = db.close()

    @Test fun permissionsFollowRole() = runBlocking {
        assertTrue(repo.createRegion(regionMgr, mapOf("name" to "غزة")) is Outcome.Failure)
        assertTrue(repo.createSquare(squareMgr, null, mapOf("name" to "م1")) is Outcome.Failure)
        assertTrue(repo.createCamp(staff, "S1", mapOf("name" to "خ1")) is Outcome.Failure)
        assertTrue(repo.createSquare(central, null, mapOf("name" to "م1")) is Outcome.Failure)
    }

    @Test fun hierarchyPushesInOrderWithRegionOnCamp() = runBlocking {
        val region = (repo.createRegion(central, mapOf("name" to "الشمال")) as Outcome.Ok).value
        val square = (repo.createSquare(central, region, mapOf("name" to "مربع 1", "phone" to "0599000000")) as Outcome.Ok).value
        val camp = repo.createCamp(central, square, mapOf("name" to "مخيم النور", "managerName" to "أبو أحمد"))
        assertTrue(camp is Outcome.Ok)
        assertEquals(region, db.directory().squareById(square)!!.regionId)
        assertEquals(region, db.directory().campById((camp as Outcome.Ok).value)!!.regionId)
        engine.pushPending()
        assertEquals(listOf("region:الشمال", "square:مربع 1@$region", "camp:مخيم النور@$region/$square"), remote.log)
    }

    @Test fun squareManagerAddsCampOnlyToOwnSquare() = runBlocking {
        db.directory().upsertSquare(SquareEntity("S1", "R1", "مربعي", null, null, null, 1))
        db.directory().upsertSquare(SquareEntity("S2", "R1", "آخر", null, null, null, 1))
        val ok = repo.createCamp(squareMgr, "S2", mapOf("name" to "مخيم"))
        assertEquals("S1", db.directory().campById((ok as Outcome.Ok).value)!!.squareId)
    }

    @Test fun regionManagerCannotTouchOtherRegionSquare() = runBlocking {
        db.directory().upsertSquare(SquareEntity("X", "R2", "غريب", null, null, null, 1))
        assertTrue(repo.createCamp(regionMgr, "X", mapOf("name" to "مخيم")) is Outcome.Failure)
    }
}
