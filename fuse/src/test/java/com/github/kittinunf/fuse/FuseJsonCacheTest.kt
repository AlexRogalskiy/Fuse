package com.github.kittinunf.fuse

import com.github.kittinunf.fuse.core.CacheBuilder
import com.github.kittinunf.fuse.core.Fuse
import com.github.kittinunf.fuse.core.build
import com.github.kittinunf.fuse.core.fetch.DiskFetcher
import com.github.kittinunf.fuse.core.fetch.get
import com.github.kittinunf.fuse.core.fetch.put
import java.io.FileNotFoundException
import java.net.URL
import java.nio.charset.Charset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import org.hamcrest.CoreMatchers.`is` as isEqualTo
import org.hamcrest.CoreMatchers.isA
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test

class FuseJsonCacheTest : BaseTestCase() {

    class JsonDataConvertible(private val charset: Charset = Charset.defaultCharset()) :
        Fuse.DataConvertible<JSONObject> {
        override fun convertFromData(bytes: ByteArray): JSONObject =
            JSONObject(bytes.toString(charset))

        override fun convertToData(value: JSONObject): ByteArray =
            value.toString().toByteArray(charset)
    }

    companion object {
        private val tempDir = createTempDir().absolutePath
        val cache = CacheBuilder.config(tempDir, JsonDataConvertible()) {
            callbackExecutor = Executor { it.run() }
        }.build()
    }

    private var hasSetUp = false

    @Before
    fun initialize() {
        if (!hasSetUp) {
            hasSetUp = true
        }
    }

    @Test
    fun firstFetch() {
        val lock = CountDownLatch(1)
        val json = assetDir.resolve("sample_json.json")

        var value: JSONObject? = null
        var error: Exception? = null

        cache.get(json) { result ->
            val (v, e) = result
            value = v
            error = e
            lock.countDown()
        }

        lock.wait()

        assertThat(value, notNullValue())
        assertThat(value!!.getString("name"), isEqualTo("Product"))
        assertThat(error, nullValue())
    }

    @Test
    fun fetchFromNetworkSuccess() {
        val lock = CountDownLatch(1)
        val httpBin = URL("https://www.httpbin.org/get")

        var value: JSONObject? = null
        var error: Exception? = null

        cache.get(httpBin) { result, _ ->
            val (v, e) = result
            value = v
            error = e
            lock.countDown()
        }

        lock.wait()
        assertThat(value, notNullValue())
        assertThat(value!!.getString("url"), isEqualTo("https://www.httpbin.org/get"))
        assertThat(error, nullValue())
    }

    @Test
    fun fetchFromNetworkFail() {
        val lock = CountDownLatch(1)
        val failedHttpBin = URL("http://www.httpbin.org/t")

        var value: JSONObject? = null
        var error: Exception? = null

        cache.get(failedHttpBin) { result, _ ->
            val (v, e) = result
            value = v
            error = e
            lock.countDown()
        }

        lock.wait()
        assertThat(value, nullValue())
        assertThat(error, notNullValue())
        assertThat(error as? FileNotFoundException, isA(FileNotFoundException::class.java))
    }

    @Test
    fun fetchWithValueJsonNotCompatible() {
        val lock = CountDownLatch(1)

        var value: JSONObject? = null
        var error: Exception? = null

        val json = assetDir.resolve("broken_json.json")
        cache.get(json) { result ->
            val (v, e) = result
            value = v
            error = e
            lock.countDown()
        }
        lock.wait()

        assertThat(value, nullValue())
        assertThat(error, notNullValue())
        assertThat(error as? JSONException, isA(JSONException::class.java))
    }

    @Test
    fun putWithValueJsonCompatible() {
        val lock = CountDownLatch(1)

        var value: JSONObject? = null
        var error: Exception? = null

        val temp = assetDir.resolve("sample_json.json").copyTo(assetDir.resolve("temp.json"), true)

        val newText = temp.readText().replace("Product", "New Product")
        temp.writeText(newText)

        cache.put(temp) { result ->
            val (v, e) = result
            value = v
            error = e
            lock.countDown()
        }
        lock.wait()

        assertThat(value, notNullValue())
        assertThat(error, nullValue())
        assertThat(value!!.getString("name"), isEqualTo("New Product"))
    }

    @Test
    fun putWithValueJsonNotCompatible() {
        val lock = CountDownLatch(1)

        var value: JSONObject? = null
        var error: Exception? = null

        val json = assetDir.resolve("broken_json.json")

        cache.put(DiskFetcher(json, cache)) { result ->
            val (v, e) = result
            value = v
            error = e
            lock.countDown()
        }
        lock.wait()

        assertThat(value, nullValue())
        assertThat(error, notNullValue())
        assertThat(error as? JSONException, isA(JSONException::class.java))
    }
}
