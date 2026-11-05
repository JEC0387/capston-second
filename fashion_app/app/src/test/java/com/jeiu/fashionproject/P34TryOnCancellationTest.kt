package com.jeiu.fashionproject

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.ui.viewmodel.TryOnViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException

/** Deferred synthetic gateway responses: no network, photos, model or device interaction. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class)
class P34TryOnCancellationTest {
    private val fake=DeferredGateway()
    private val model=TryOnViewModel(SavedStateHandle(mapOf("engine" to "local"))).also {it.gateway=fake}
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private val person=Uri.parse("content://p34-synthetic/person")
    private val garment=TryOnGarment("a","Synthetic A","상의","content://p34-synthetic/a")
    @Before fun setup() {assertTrue(LocalVtonSession.configure("https://synthetic.example","p34-synthetic-token-000000000000000000"))}
    @After fun cleanup() {model.viewModelScope.cancel();LocalVtonSession.clear();idle()}
    private fun idle()=shadowOf(Looper.getMainLooper()).idle()
    private fun start(item:TryOnGarment=garment) {
        model.generate(app,person,listOf(item),true);idle()
        assertTrue(model.state.pending);assertNotNull(model.requestId)
    }
    @Test fun repeatedCancelWhileResponsePendingSendsOneDelete() {
        start();model.cancel();model.cancel();idle()
        assertEquals(1,fake.cancels)
        assertEquals(1,fake.posts)
    }
    @Test fun lateSuccessfulCancellationCannotChangeAnotherRequestsState() { lateCancellation(false) }
    @Test fun lateFailedCancellationCannotChangeAnotherRequestsState() { lateCancellation(true) }
    private fun lateCancellation(fail:Boolean) {
        start();val oldId=model.requestId
        model.cancel();idle();assertEquals(1,fake.cancels)
        fake.statusGate.complete(TryOnJob("completed",null,1,1,true));idle()
        assertFalse(model.state.pending)
        fake.statusGate=CompletableDeferred()
        start(garment.copy(id="b",image="content://p34-synthetic/b"))
        val newId=model.requestId;assertNotEquals(oldId,newId);assertNull(model.state.error)
        if(fail) fake.cancelGate.completeExceptionally(IOException("synthetic cancel failure")) else fake.cancelGate.complete(Unit)
        idle()
        assertEquals(newId,model.requestId);assertTrue(model.state.pending)
        assertNull("An old cancellation must not alter a new request",model.state.error)
        assertEquals(2,fake.posts)
    }
    @Test fun explicitCancelRetryIsAllowedAfterFailure() {
        start();model.cancel();idle()
        fake.cancelGate.completeExceptionally(IOException("synthetic cancel failure"));idle()
        assertNotNull(model.state.error)
        fake.cancelGate=CompletableDeferred()
        model.cancel();idle()
        assertEquals(2,fake.cancels);assertEquals(1,fake.posts)
    }
    @Test fun deferredPostCancellationAndRepeatedTapShareOneDelete() {
        fake.submitGate=CompletableDeferred()
        start();model.cancel();idle();assertEquals(0,fake.cancels)
        fake.submitGate!!.complete(Unit);idle();assertEquals(1,fake.cancels)
        model.cancel();idle();assertEquals(1,fake.cancels)
        fake.cancelGate.complete(Unit);idle()
        assertEquals(1,fake.posts);assertTrue(model.state.pending)
    }
    @Test fun clearingViewModelDoesNotConvertCancellationToFailureMessage() {
        start();model.cancel();idle()
        model.viewModelScope.cancel();idle()
        assertNull(model.state.error)
    }
    private class DeferredGateway:TryOnGateway {
        var posts=0;var cancels=0
        var statusGate=CompletableDeferred<TryOnJob>()
        var cancelGate=CompletableDeferred<Unit>()
        var submitGate:CompletableDeferred<Unit>?=null
        override suspend fun image(context:Context,source:String)="synthetic-only"
        override suspend fun check(connection:TryOnConnection) {}
        override suspend fun submit(connection:TryOnConnection,id:String,person:String,garments:List<Pair<String,String>>) {posts++;submitGate?.await()}
        override suspend fun status(connection:TryOnConnection,id:String)=statusGate.await()
        override suspend fun result(connection:TryOnConnection,id:String)=byteArrayOf(1,2,3)
        override suspend fun cancel(connection:TryOnConnection,id:String) {cancels++;cancelGate.await()}
    }
}
