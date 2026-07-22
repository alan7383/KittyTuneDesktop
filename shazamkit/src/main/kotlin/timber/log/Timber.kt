package timber.log

object Timber {
    fun e(message: String, vararg args: Any?) { println(message.format(*args)) }
    fun d(message: String, vararg args: Any?) { println(message.format(*args)) }
    fun i(message: String, vararg args: Any?) { println(message.format(*args)) }
    fun w(message: String, vararg args: Any?) { println(message.format(*args)) }
    
    fun e(t: Throwable, message: String, vararg args: Any?) { println(message.format(*args) + " " + t.message) }

    fun tag(tag: String): Tree {
        return Tree(tag)
    }

    class Tree(val tag: String) {
        fun e(message: String, vararg args: Any?) { println("[$tag] " + message.format(*args)) }
        fun e(t: Throwable, message: String, vararg args: Any?) { println("[$tag] " + message.format(*args) + " " + t.message) }
        fun d(message: String, vararg args: Any?) { println("[$tag] " + message.format(*args)) }
        fun d(t: Throwable, message: String, vararg args: Any?) { println("[$tag] " + message.format(*args) + " " + t.message) }
        fun i(message: String, vararg args: Any?) { println("[$tag] " + message.format(*args)) }
        fun i(t: Throwable, message: String, vararg args: Any?) { println("[$tag] " + message.format(*args) + " " + t.message) }
        fun w(message: String, vararg args: Any?) { println("[$tag] " + message.format(*args)) }
        fun w(t: Throwable, message: String, vararg args: Any?) { println("[$tag] " + message.format(*args) + " " + t.message) }
    }
}
