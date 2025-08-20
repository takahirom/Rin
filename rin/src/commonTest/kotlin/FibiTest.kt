import kotlin.test.Test
import kotlin.test.assertEquals

class FibiTest {

    @Test
    fun test3rdElement() {
        val fibonacci = listOf(0, 1, 1, 2, 3, 5, 8, 13)
        assertEquals(1, fibonacci[2])
    }
}