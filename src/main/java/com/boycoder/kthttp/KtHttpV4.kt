package com.boycoder.kthttp

import com.boycoder.kthttp.annotations.Field
import com.boycoder.kthttp.annotations.GET
import com.google.gson.Gson
import com.google.gson.internal.`$Gson$Types`.getRawType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Proxy
import java.lang.reflect.Type
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.system.measureTimeMillis

// https://trendings.herokuapp.com/repo?lang=java&since=weekly

//suspend fun <T> KtCall<T>.await(): T =
//    suspendCoroutine { continuation ->
//        call(object : Callback<T> {
//            override fun onSuccess(data: T) {
//                if (data != null) {
//                    continuation.resumeWith(Result.success(data))
//                } else {
//                    continuation.resumeWith(Result.failure(NullPointerException()))
//                }
//            }
//
//            override fun onFail(throwable: Throwable) {
//                continuation.resumeWith(Result.failure(throwable))
//            }
//        })
//    }

//suspend fun <T : Any> KtCall<T>.await(): T =
//    suspendCoroutine { continuation ->
//        call(object : Callback<T> {
//            override fun onSuccess(data: T) {
//                println("Request success!")
//                continuation.resume(data)
//            }
//
//            override fun onFail(throwable: Throwable) {
//                println("Request fail!：$throwable")
//                continuation.resumeWithException(throwable)
//            }
//        })
//    }

suspend fun <T : Any> KtCall<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        val call = call(object : Callback<T> {
            override fun onSuccess(data: T) {
                println("Request success!")
                continuation.resume(data)
            }

            override fun onFail(throwable: Throwable) {
                println("Request fail!：$throwable")
                continuation.resumeWithException(throwable)
            }
        })

        continuation.invokeOnCancellation {
            println("Call cancelled!")
            call.cancel()
        }
    }

fun main() = runBlocking {
    val start = System.currentTimeMillis()
    val deferred = async {
        KtHttpV3.create(ApiServiceV3::class.java)
            .repos(lang = "Kotlin", since = "weekly")
            .await()
    }

    deferred.invokeOnCompletion {
        println("invokeOnCompletion!")
    }
    delay(50L)

    deferred.cancel()
    println("Time cancel: ${System.currentTimeMillis() - start}")

    try {
        println(deferred.await())
    } catch (e: Exception) {
        println("Time exception: ${System.currentTimeMillis() - start}")
        println("Catch exception:$e")
    } finally {
        println("Time total: ${System.currentTimeMillis() - start}")
    }
}


//fun main() = runBlocking {
//    val ktCall = KtHttpV3.create(ApiServiceV3::class.java)
//        .repos(lang = "Kotlin", since = "weekly")
//
//    val result = ktCall.await()
//    println(result)
//}
