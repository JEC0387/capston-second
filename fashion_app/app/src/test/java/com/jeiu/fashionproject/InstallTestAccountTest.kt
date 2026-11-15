package com.jeiu.fashionproject

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class InstallTestAccountTest {
    private val base = "https://approved.invalid:443"
    private val issued = """{"accessToken":"${"A".repeat(43)}","recoveryCode":"${"B".repeat(43)}","dataEnabled":true}"""
    private val valid = """{"authenticated":true,"dataEnabled":true}"""

    @Test fun noApprovalOrWrongServerCannotRegister() = runBlocking {
        var calls = 0
        val c = TestAccountConnection({ JSONObject() }, { true }, { base }, { true }) { _, _, _ -> calls++; error("network prohibited") }
        c.load()
        assertFalse(c.enroll(false, "APPROVED-1", base))
        assertFalse(c.enroll(true, "", base))
        assertFalse(c.enroll(true, "APPROVED-1", "https://other.invalid"))
        assertNull(c.session()); assertEquals(0, calls)
        val blocked = TestAccountConnection({ JSONObject() }, { true }, { base }, { false }) { _,_,_ -> error("disabled build") }
        blocked.load(); assertFalse(blocked.enroll(true, "APPROVED-1", base))
    }
    @Test fun freshAccountUsesSeparateEncryptedVaultAndNeverChangesExistingData() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val old = CredentialVault(app) { key }
        assertTrue(old.write(JSONObject().put("token", "old-account").put("enabled", false)))
        val names = listOf("data_auth", "db_sync", "wardrobe_store", "body_measurements")
        names.drop(1).forEach { app.getSharedPreferences(it, 0).edit().putString("sentinel", "preserve").commit() }
        val before = names.associateWith { app.getSharedPreferences(it, 0).all.toMap() }
        val vault = CredentialVault(app, "installtest_account") { key }
        val calls = mutableListOf<String>()
        val c = TestAccountConnection(vault::read, vault::write, { base }, { true }) { destination, action, token ->
            assertEquals(base, destination); calls += action
            if(action == "register") { assertNull(token); SafeAccountHttp.Reply(201, issued) }
            else { assertEquals("A".repeat(43), token); SafeAccountHttp.Reply(200, valid) }
        }
        c.load(); assertTrue(c.enroll(true, "APPROVED-1", base)); assertNotNull(c.session())
        assertEquals(listOf("register", "status"), calls)
        assertFalse(c.enroll(true, "APPROVED-2", base))
        names.forEach { assertEquals(before[it], app.getSharedPreferences(it, 0).all) }
        assertFalse(app.getSharedPreferences("installtest_account", 0).all.toString().contains("A".repeat(43)))
        val restored = TestAccountConnection(vault::read, vault::write, { base }, { true }) { _, action, _ ->
            assertEquals("status", action); SafeAccountHttp.Reply(200, valid)
        }
        restored.load(); assertNull(restored.session()); assertTrue(restored.verify()); assertNotNull(restored.session())
    }
    @Test fun lostReplyOrProcessRestartCannotCreateSecondAccount() = runBlocking {
        var saved = JSONObject(); var calls = 0
        val c = TestAccountConnection({ saved }, { saved = JSONObject(it.toString()); true }, { base }, { true }) { _, _, _ ->
            calls++; throw java.io.IOException("synthetic lost reply")
        }
        c.load(); assertFalse(c.enroll(true, "APPROVED-1", base)); assertEquals("outcome_unknown", c.state.value)
        c.load(); assertFalse(c.enroll(true, "APPROVED-1", base)); assertFalse(c.verify())
        assertEquals(1, calls); assertNull(c.session())
    }
    @Test fun duplicateConcurrentClicksOnlyPostOnce() = runBlocking {
        var posts = 0
        val c = TestAccountConnection({ JSONObject() }, { true }, { base }, { true }) { _, action, _ ->
            if(action == "register") { posts++; delay(100); SafeAccountHttp.Reply(201, issued) }
            else SafeAccountHttp.Reply(200, valid)
        }
        c.load()
        val results = listOf(async { c.enroll(true,"APPROVED-1",base) }, async { c.enroll(true,"APPROVED-1",base) }).awaitAll()
        assertEquals(1, posts); assertEquals(1, results.count { it })
    }
    @Test fun cancellationKeepsJournalAndDoesNotRetry() = runBlocking {
        var saved = JSONObject(); var calls = 0
        val started = CompletableDeferred<Unit>()
        val c = TestAccountConnection({saved}, {saved=it;true}, {base},{true}) { _,_,_ -> calls++; started.complete(Unit); awaitCancellation() }
        c.load(); val job=launch { c.enroll(true,"APPROVED-1",base) }
        started.await(); job.cancelAndJoin(); c.load()
        assertFalse(c.enroll(true,"APPROVED-2",base)); assertEquals(1,calls)
    }
    @Test fun invalidDisabledAndUnauthorizedRepliesNeverExposeSession() = runBlocking {
        for(reply in listOf(SafeAccountHttp.Reply(503,"{}"), SafeAccountHttp.Reply(201,"{}"),
            SafeAccountHttp.Reply(201,issued.replace("true","false")))) {
            val c=TestAccountConnection({JSONObject()},{true},{base},{true}) { _,_,_->reply }
            c.load(); assertFalse(c.enroll(true,"APPROVED-1",base)); assertNull(c.session())
        }
        val c=TestAccountConnection({JSONObject()},{true},{base},{true}) { _,action,_->
            if(action=="register") SafeAccountHttp.Reply(201,issued) else SafeAccountHttp.Reply(401,"{}") }
        c.load(); assertFalse(c.enroll(true,"APPROVED-1",base)); assertNull(c.session())
    }
    @Test fun serverChangeNeverTransmitsSavedCredentialToAnotherHost() = runBlocking {
        var current = base; var calls=0
        val c=TestAccountConnection({JSONObject()},{true},{current},{true}) { _,action,_->
            calls++; if(action=="register") SafeAccountHttp.Reply(201,issued) else SafeAccountHttp.Reply(200,valid) }
        c.load(); assertTrue(c.enroll(true,"APPROVED-1",base)); current="https://other.invalid:443"
        assertNull(c.session()); assertFalse(c.verify()); assertEquals(2,calls)
    }
    @Test fun storageFailureBlocksNetworkAndDoesNotOverwriteExistingRecord() = runBlocking {
        val c=TestAccountConnection({JSONObject()},{false},{base},{true}) { _,_,_->error("no network") }
        c.load(); assertFalse(c.enroll(true,"APPROVED-1",base)); assertEquals("storage_unavailable",c.state.value)
        val existing=TestAccountConnection({JSONObject().put("origin",base).put("token","A".repeat(43))},
            {error("must not overwrite")},{base},{true}) { _,_,_->error("no registration") }
        existing.load(); assertFalse(existing.enroll(true,"APPROVED-1",base))
    }
    @Test fun testCredentialIsExcludedFromCloudAndDeviceTransfer() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        for ((resource, expected) in listOf(R.xml.backup_rules to 1, R.xml.data_extraction_rules to 2)) {
            val parser=app.resources.getXml(resource); var count=0
            while(parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if(parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "exclude" &&
                    parser.getAttributeValue(null,"domain") == "sharedpref" &&
                    parser.getAttributeValue(null,"path") == "installtest_account.xml") count++
                parser.next()
            }
            parser.close(); assertEquals(expected,count)
        }
    }
    @Test fun copyingCiphertextBetweenAccountVaultsFailsAuthentication() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val key=KeyGenerator.getInstance("AES").apply {init(256)}.generateKey()
        assertTrue(CredentialVault(app) {key}.write(JSONObject().put("token","old")))
        val ciphertext=app.getSharedPreferences("data_auth",0).getString("vault_v1",null)
        app.getSharedPreferences("installtest_account",0).edit().putString("vault_v1",ciphertext).commit()
        assertTrue(runCatching { CredentialVault(app,"installtest_account") {key}.read() }.isFailure)
        assertEquals("old", CredentialVault(app) {key}.read().getString("token"))
    }

}
