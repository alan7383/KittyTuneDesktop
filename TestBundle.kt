import androidx.savedstate.SavedState
import androidx.savedstate.read
fun test(state: SavedState) { val s: String? = state.read { getString("id") }; println(s) }
