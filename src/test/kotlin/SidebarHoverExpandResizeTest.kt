import com.alananasss.kittytune.core.Application
import com.alananasss.kittytune.ui.library.LibraryViewModel
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SidebarHoverExpandResizeTest {

    @Test
    fun `dragging while hover-expanded preserves collapsed state and updates width`() {
        val vm = LibraryViewModel(Application())
        vm.isSidebarCollapsed = true
        vm.sidebarWidth = 300f

        // 1. Drag starts while hover-expanded
        vm.sidebarDragStart(isHoverExpanded = true)

        // 2. Drag to stretch panel wider (+80dp -> 380dp)
        vm.sidebarDragBy(deltaDp = 80f, isHoverExpanded = true)

        assertEquals(380f, vm.sidebarWidth, "Sidebar width should stretch to 380dp")
        assertTrue(vm.isSidebarCollapsed, "isSidebarCollapsed must remain true so hover-expand remains active")

        // 3. Drag ends
        vm.sidebarDragEnd(keepCollapsed = true)

        assertTrue(vm.isSidebarCollapsed, "After drag end, sidebar must still be collapsed")
        assertEquals(380f, vm.sidebarWidth, "New width 380dp must be retained for next hover expand")
    }

    @Test
    fun `standard drag from collapsed uncollapses when dragged past snap threshold`() {
        val vm = LibraryViewModel(Application())
        vm.isSidebarCollapsed = true
        vm.sidebarWidth = 300f

        // Normal drag from collapsed rail (72dp + 120dp = 192dp > 176dp threshold)
        vm.sidebarDragStart(isHoverExpanded = false)
        vm.sidebarDragBy(deltaDp = 120f, isHoverExpanded = false)

        assertFalse(vm.isSidebarCollapsed, "Standard drag past 176dp should uncollapse the sidebar")
        vm.sidebarDragEnd(keepCollapsed = false)
        assertFalse(vm.isSidebarCollapsed, "Should save uncollapsed state")
    }

    @Test
    fun `standard drag from expanded collapses when dragged below snap threshold`() {
        val vm = LibraryViewModel(Application())
        vm.isSidebarCollapsed = false
        vm.sidebarWidth = 300f

        // Normal drag to left (300dp - 150dp = 150dp < 176dp threshold)
        vm.sidebarDragStart(isHoverExpanded = false)
        vm.sidebarDragBy(deltaDp = -150f, isHoverExpanded = false)

        assertTrue(vm.isSidebarCollapsed, "Dragging below 176dp should collapse the sidebar")
        vm.sidebarDragEnd(keepCollapsed = false)
        assertTrue(vm.isSidebarCollapsed, "Should save collapsed state")
    }

    @Test
    fun `collapseSidebar and expandSidebar explicitly set collapsed state`() {
        val vm = LibraryViewModel(Application())

        vm.collapseSidebar()
        assertTrue(vm.isSidebarCollapsed)

        vm.expandSidebar()
        assertFalse(vm.isSidebarCollapsed)
    }
}
