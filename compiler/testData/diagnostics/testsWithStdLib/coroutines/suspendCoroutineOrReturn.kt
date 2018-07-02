// !API_VERSION: 1.3
// !DIAGNOSTICS: -UNUSED_PARAMETER
// !CHECK_TYPE
// LANGUAGE_VERSION: 1.3
// SKIP_TXT
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

class Controller {
    <!EXPERIMENTAL_FEATURE_WARNING!>suspend<!> fun noParams(): Unit = suspendCoroutineOrReturn {
        if (hashCode() % 2 == 0) {
            it.resume(Unit)
            COROUTINE_SUSPENDED
        }
        else {
            Unit
        }
    }
    <!EXPERIMENTAL_FEATURE_WARNING!>suspend<!> fun yieldString(value: String) = suspendCoroutineOrReturn<Int> {
        it.resume(1)
        it checkType { _<Continuation<Int>>() }
        it.<!TYPE_INFERENCE_CONFLICTING_SUBSTITUTIONS!>resume<!>("")

        // We can return anything here, 'suspendCoroutineOrReturn' is not very type-safe
        // Also we can call resume and then return the value too, but it's still just our problem
        "Not-int"
    }
}

fun builder(c: <!EXPERIMENTAL_FEATURE_WARNING!>suspend<!> Controller.() -> Unit) {}

fun test() {
    <!EXPERIMENTAL_FEATURE_WARNING!>builder<!> {
        noParams() checkType { _<Unit>() }
        yieldString("abc") checkType { _<Int>() }
    }
}
