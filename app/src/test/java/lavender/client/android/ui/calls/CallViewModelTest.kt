package lavender.client.android.ui.calls

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CallViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var viewModel: CallViewModel

    @Before
    fun setup() {
        viewModel = CallViewModel(scope = testScope)
    }

    @After
    fun tearDown() {
        testScope.coroutineContext[Job]?.cancel()
    }

    @Test
    fun timerText_initialValue() = testScope.runTest {
        assertEquals("00:00", viewModel.timerText.value)
    }

    @Test
    fun startTimer_updatesTimerText() = testScope.runTest {
        viewModel.startTimer()
        advanceTimeBy(5100)
        viewModel.stopTimer()
        assertEquals("00:05", viewModel.timerText.value)
    }

    @Test
    fun startTimer_idempotent() = testScope.runTest {
        viewModel.startTimer()
        advanceTimeBy(1100)
        val time1 = viewModel.timerText.value
        viewModel.startTimer()
        advanceTimeBy(1100)
        viewModel.stopTimer()
        val time2 = viewModel.timerText.value
        val parts1 = time1.split(":").last().toInt()
        val parts2 = time2.split(":").last().toInt()
        assertTrue(parts2 > parts1)
    }

    @Test
    fun stopTimer_stopsUpdates() = testScope.runTest {
        viewModel.startTimer()
        advanceTimeBy(3100)
        viewModel.stopTimer()
        val timeAtStop = viewModel.timerText.value
        advanceTimeBy(5000)
        assertEquals(timeAtStop, viewModel.timerText.value)
    }

    @Test
    fun timerText_formatsHours() = testScope.runTest {
        viewModel.startTimer()
        advanceTimeBy(3661100)
        viewModel.stopTimer()
        val time = viewModel.timerText.value
        val parts = time.split(":")
        assertEquals(3, parts.size)
        assertEquals("01", parts[0])
        assertEquals("01", parts[1])
        assertEquals("01", parts[2])
    }
}
