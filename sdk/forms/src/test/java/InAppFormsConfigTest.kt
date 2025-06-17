
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.forms.InAppFormsConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Test

class InAppFormsConfigTest : BaseTest() {
    @Test
    fun `test InAppFormsConfig default values`() = assertEquals(
        3600L,
        InAppFormsConfig().getSessionTimeoutDuration().inWholeSeconds
    )

    @Test
    fun `test InAppFormsConfig custom values`() = assertEquals(
        10_000L,
        InAppFormsConfig(10.seconds).getSessionTimeoutDuration().inWholeMilliseconds
    )

    @Test
    fun `test InAppFormsConfig negative value`() = assertEquals(
        0L,
        InAppFormsConfig((-10).seconds).getSessionTimeoutDuration().inWholeMilliseconds
    )

    @Test
    fun `test InAppFormsConfig max value`() = assertEquals(
        Long.MAX_VALUE,
        InAppFormsConfig(Long.MAX_VALUE.seconds).getSessionTimeoutDuration().inWholeMilliseconds
    )

    @Test
    fun `test InAppFormsConfig infinite value`() = assertEquals(
        Long.MAX_VALUE,
        InAppFormsConfig(Duration.INFINITE).getSessionTimeoutDuration().inWholeMilliseconds
    )
}
