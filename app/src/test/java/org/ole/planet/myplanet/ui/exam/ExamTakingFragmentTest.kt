package org.ole.planet.myplanet.ui.exam

import org.junit.Test
import org.ole.planet.myplanet.base.BaseExamFragment

class ExamTakingFragmentTest {

    @Test
    fun testSaveCourseProgressOverridesBase() {
        val baseMethod = BaseExamFragment::class.java.getDeclaredMethod(
            "saveCourseProgress",
            String::class.java,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        )
        assert(baseMethod != null)
        assert(java.lang.reflect.Modifier.isAbstract(baseMethod.modifiers))

        val childMethod = ExamTakingFragment::class.java.getDeclaredMethod(
            "saveCourseProgress",
            String::class.java,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        )
        assert(childMethod != null)
        assert(!java.lang.reflect.Modifier.isAbstract(childMethod.modifiers))
    }
}
