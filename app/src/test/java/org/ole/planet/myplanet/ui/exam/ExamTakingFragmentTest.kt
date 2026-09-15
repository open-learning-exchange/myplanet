package org.ole.planet.myplanet.ui.exam

import java.lang.reflect.Modifier
import org.junit.Test
import org.ole.planet.myplanet.base.BaseExamFragment

class ExamTakingFragmentTest {

    @Test
    fun testSaveCourseProgressOverridesBase() {
        val baseMethod = BaseExamFragment::class.java.getDeclaredMethod(
            "saveCourseProgress",
            String::class.java,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            String::class.java
        )
        assert(baseMethod != null)
        assert(Modifier.isAbstract(baseMethod.modifiers))

        val childMethod = ExamTakingFragment::class.java.getDeclaredMethod(
            "saveCourseProgress",
            String::class.java,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            String::class.java
        )
        assert(childMethod != null)
        assert(!Modifier.isAbstract(childMethod.modifiers))
    }
}
