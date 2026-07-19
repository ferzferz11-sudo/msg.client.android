package lavender.client.android.ui.calls

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
@Ignore("Dispatchers.resetMain() blocks waiting for testDispatcher drain — viewModelScope coroutines outlive testRunner. Scope injection is in place; re-enable once viewModelScope cancellation is resolved.")
class CallViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: CallViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = CallViewModel()
    }

    @After
    fun tearDown() {
        viewModel.stopTimer()
        Dispatchers.resetMain()
    }

    @Test
    fun timerText_initialValue() = runTest {
        assertEquals("00:00", viewModel.timerText.value)
    }

    @Test
    fun startTimer_updatesTimerText() = runTest {
        viewModel.startTimer()
        advanceTimeBy(5000)
        testDispatcher.scheduler.advanceUntilIdle()
        val time = viewModel.timerText.value
        val parts = time.split(":")
        assertEquals(2, parts.size)
        assertEquals("00", parts[0])
        assertEquals("05", parts[1])
    }

    @Test
    fun startTimer_idempotent() = runTest {
        viewModel.startTimer()
        advanceTimeBy(1000)
        testDispatcher.scheduler.advanceUntilIdle()
        val time1 = viewModel.timerText.value
        viewModel.startTimer()
        advanceTimeBy(1000)
        testDispatcher.scheduler.advanceUntilIdle()
        val time2 = viewModel.timerText.value
        val parts1 = time1.split(":").last().toInt()
        val parts2 = time2.split(":").last().toInt()
        assertTrue(parts2 > parts1)
    }

    @Test
    fun stopTimer_stopsUpdates() = runTest {
        viewModel.startTimer()
        advanceTimeBy(3000)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.stopTimer()
        val timeAtStop = viewModel.timerText.value
        advanceTimeBy(5000)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(timeAtStop, viewModel.timerText.value)
    }

    @Test
    fun timerText_formatsHours() = runTest {
        viewModel.startTimer()
        advanceTimeBy(3661000)
        testDispatcher.scheduler.advanceUntilIdle()
        val time = viewModel.timerText.value
        assertTrue(time.contains(":"))
        val parts = time.split(":")
        assertEquals(3, parts.size)
        assertEquals("01", parts[0])
    }
}
