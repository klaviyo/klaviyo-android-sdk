
import com.klaviyo.fixtures.BaseTest
import com.klaviyo.forms.InAppFormsConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class InAppFormsConfigTest : BaseTest() {
    @Test
    fun `test InAppFormsConfig default values`() = assertEquals(
        3600L,
        InAppFormsConfig().getSessionTimeoutDuration()
    )

    @Test
    fun `test InAppFormsConfig custom values`() = assertEquals(
        10L,
        InAppFormsConfig(10_000L).getSessionTimeoutDurationInMillis()
    )

    @Test
    fun `test InAppFormsConfig negative value`() = assertEquals(
        InAppFormsConfig(-10).getSessionTimeoutDurationInMillis(),
        0L
    )

    @Test
    fun `test InAppFormsConfig max value`() = assertEquals(
        Long.MAX_VALUE,
        InAppFormsConfig(Long.MAX_VALUE).getSessionTimeoutDurationInMillis()
    )
}
