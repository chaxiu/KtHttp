package com.boycoder.kthttp

import com.boycoder.kthttp.annotations.Field
import com.boycoder.kthttp.annotations.GET
import com.google.gson.Gson
import com.google.gson.internal.`$Gson$Types`.getRawType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Proxy

// https://trendings.herokuapp.com/repo?lang=java&since=weekly

interface ApiServiceV6 {
    @GET("/repo")
    fun repos(
        @Field("lang") lang: String,
        @Field("since") since: String
    ): KtCall<RepoList>

    @GET("/repo")
    fun reposSync(
        @Field("lang") lang: String,
        @Field("since") since: String
    ): RepoList


    @GET("/repo")
    fun reposFlow(
        @Field("lang") lang: String,
        @Field("since") since: String
    ): Flow<RepoList>
}

object KtHttpV6 {

    private var okHttpClient: OkHttpClient = OkHttpClient()
    private var gson: Gson = Gson()
    var baseUrl = "https://trendings.herokuapp.com"

    fun <T : Any> create(service: Class<T>): T {
        return Proxy.newProxyInstance(
            service.classLoader,
            arrayOf<Class<*>>(service)
        ) { proxy, method, args ->
            val annotations = method.annotations
            for (annotation in annotations) {
                if (annotation is GET) {
                    val url = baseUrl + annotation.value
                    return@newProxyInstance invoke<T>(url, method, args!!)
                }
            }
            return@newProxyInstance null

        } as T
    }

    private fun <T : Any> invoke(path: String, method: Method, args: Array<Any>): Any? {
        if (method.parameterAnnotations.size != args.size) return null

        var url = path
        val parameterAnnotations = method.parameterAnnotations
        for (i in parameterAnnotations.indices) {
            for (parameterAnnotation in parameterAnnotations[i]) {
                if (parameterAnnotation is Field) {
                    val key = parameterAnnotation.value
                    val value = args[i].toString()
                    if (!url.contains("?")) {
                        url += "?$key=$value"
                    } else {
                        url += "&$key=$value"
                    }

                }
            }
        }

        val request = Request.Builder()
            .url(url)
            .build()

        val call = okHttpClient.newCall(request)

        return when {
            isKtCallReturn(method) -> {
                val genericReturnType = getTypeArgument(method)
                KtCall<T>(call, gson, genericReturnType)
            }
            isFlowReturn(method) -> {
                logX("Start out")
                flow<T> {
                    logX("Start in")
                    val genericReturnType = getTypeArgument(method)
                    val response = okHttpClient.newCall(request).execute()
                    val json = response.body?.string()
                    val result = gson.fromJson<T>(json, genericReturnType)
                    logX("Start emit")
                    emit(result)
                    logX("End emit")
                }
            }
            else -> {
                val response = okHttpClient.newCall(request).execute()

                val genericReturnType = method.genericReturnType
                val json = response.body?.string()
                gson.fromJson(json, genericReturnType)
            }
        }
    }

    private fun getTypeArgument(method: Method) =
        (method.genericReturnType as ParameterizedType).actualTypeArguments[0]

    private fun isKtCallReturn(method: Method) =
        getRawType(method.genericReturnType) == KtCall::class.java

    private fun isFlowReturn(method: Method) =
        getRawType(method.genericReturnType) == Flow::class.java

}

fun main() {
    // 协程作用域外
    val flow = KtHttpV6.create(ApiServiceV6::class.java)
        .reposFlow(lang = "Kotlin", since = "weekly")
        .flowOn(Dispatchers.IO)
        .catch { println("Catch: $it") }

    runBlocking {
        // 协程作用域内
        flow.collect {
            logX("${it.count}")
        }
    }
}


private suspend fun testFlow() =
    KtHttpV6.create(ApiServiceV6::class.java)
        .reposFlow(lang = "Kotlin", since = "weekly")
        .flowOn(Dispatchers.IO)
        .catch { println("Catch: $it") }
        .collect {
            logX("${it.count}")
        }
