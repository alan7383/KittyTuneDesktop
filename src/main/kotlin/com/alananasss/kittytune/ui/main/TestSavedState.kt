import androidx.savedstate.SavedState
import androidx.savedstate.read

fun printSavedState(state: SavedState) {
    println(state::class.java.name)
    val methods = state::class.java.methods
    for (m in methods) {
        println(m.name)
    }
}
