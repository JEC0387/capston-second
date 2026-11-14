package com.jeiu.fashionproject

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.jeiu.fashionproject.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import javax.crypto.KeyGenerator
import javax.net.ssl.HttpsURLConnection

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BodyAccountOwnershipTest {
    private val app = RuntimeEnvironment.getApplication() as Context
    private val server = MockWebServer()
    private val priorServer = BackendApi.BASE_URL
    private val priorTls = HttpsURLConnection.getDefaultSSLSocketFactory()
    private lateinit var vault: CredentialVault
    private lateinit var origin: String
    private val a = "A".repeat(43)
    private val b = "B".repeat(43)
    private val c = "C".repeat(43)
    private val measurements = ManualBodyMeasurements(160f,39f,88f,71f,95f,null,null,123L)
    private val avatar = checkNotNull(AvatarAnalysisInput.build(160f,55f,mapOf(
        AvatarPart.SHOULDER to 39f,AvatarPart.CHEST to 88f,AvatarPart.WAIST to 71f,AvatarPart.HIP to 95f),null,123L))
    private val manual get() = app.getSharedPreferences("manual_body_measurements",0)
    private val profiles get() = app.getSharedPreferences("avatar_visual_profile_v1",0)

    @Before fun setup() {
        resetAuth()
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1").addSubjectAlternativeName("::1").build()
        server.useHttps(HandshakeCertificates.Builder().heldCertificate(cert).build().sslSocketFactory(),false)
        server.start()
        HttpsURLConnection.setDefaultSSLSocketFactory(HandshakeCertificates.Builder()
            .addTrustedCertificate(cert.certificate).build().sslSocketFactory())
        origin = server.url("/").toString().trimEnd('/')
        BackendApi.BASE_URL = origin
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        vault = CredentialVault(app) { key }
        vault.write(JSONObject().put("token",a).put("origin",origin).put("enabled",true))
        app.getSharedPreferences("auth",0).edit().putString("mode","SIGNED_IN").putBoolean("bearer_v2",true).commit()
        AuthStore.load(app)
        DataAuth.initialize(app,vault)
    }

    @After fun finish() {
        resetAuth()
        HttpsURLConnection.setDefaultSSLSocketFactory(priorTls)
        BackendApi.BASE_URL=priorServer
        // Robolectric can reuse the SDK classloader across test classes. Do not
        // leave this fixture's signed-in singleton active for the following UI tests.
        app.getSharedPreferences("auth",0).edit().clear().commit()
        AuthStore.load(app)
        ManualBodyStore.load(app)
        AvatarProfileStore.load(app)
        server.shutdown()
    }

    private fun resetAuth() {
        // Only Robolectric's isolated process fixtures; never a device or real vault.
        for (name in listOf("context","vault","binding","recovery","requestedRecovery","requestedRecoveryServer"))
            DataAuth::class.java.getDeclaredField(name).apply { isAccessible=true }.set(DataAuth,null)
        for (name in listOf("storageLocked","enabled","restorePending","legacyPending"))
            DataAuth::class.java.getDeclaredField(name).apply { isAccessible=true }.setBoolean(DataAuth,false)
        DataAuth::class.java.getDeclaredField("bodyOwners").apply { isAccessible=true }.set(DataAuth,emptyMap<String,String>())
    }

    private fun seedLegacy(token: String=a) {
        val old = BodyMeasurementScope.legacyScope(origin,token)
        profiles.edit().putString(old+"profile",AvatarProfileStore.encode(avatar))
            .putString(old+"profile_pre_cm_preserved","original-backup").commit()
        manual.edit().putBoolean(old+"save",true).putLong(old+"entered_at",123L)
            .putFloat(old+"height",160f).putFloat(old+"shoulder",39f).putFloat(old+"chest",88f)
            .putFloat(old+"waist",71f).putFloat(old+"hip",95f).commit()
    }
    private fun reply(json: JSONObject, code:Int=200) = server.enqueue(MockResponse().setResponseCode(code)
        .setHeader("Content-Type","application/json").setBody(json.toString()))
    private fun status(id:String?="1") = reply(JSONObject().put("authenticated",true).put("dataEnabled",true).put("accountId",id))
    private fun reload() { ManualBodyStore.load(app); AvatarProfileStore.load(app) }
    private fun assertRestored() {
        reload();assertEquals(measurements,ManualBodyStore.current.value);assertEquals(avatar,AvatarProfileStore.current.value)
    }

    @Test fun reproducesP69TokenNamespaceLossAndStableIdentityFix() {
        seedLegacy()
        assertFalse(profiles.contains(BodyMeasurementScope.legacyScope(origin,b)+"profile"))
        val one=BodyMeasurementScope.resolve(AuthStore.Mode.SIGNED_IN,false,a,origin,"1")
        assertEquals(one,BodyMeasurementScope.resolve(AuthStore.Mode.SIGNED_IN,false,b,origin,"1"))
        assertNotEquals(one,BodyMeasurementScope.resolve(AuthStore.Mode.SIGNED_IN,false,a,origin,"2"))
        assertNotEquals(one,BodyMeasurementScope.resolve(AuthStore.Mode.SIGNED_IN,false,a,"https://other.test","1"))
        assertNull(BodyMeasurementScope.resolve(AuthStore.Mode.SIGNED_IN,true,a,origin,"1"))
        assertNull(BodyMeasurementScope.resolve(AuthStore.Mode.SIGNED_IN,false,null,origin,"1"))
    }

    @Test fun upgradeMigrationRecoveryAndProcessRestartKeepBothStoresAndOriginals() = runBlocking {
        seedLegacy();val oldManual=manual.all.toMap();val oldAvatar=profiles.all.toMap()
        status();assertTrue(DataAuth.validateSession());assertRestored()
        assertTrue(BodyMeasurementScope.current()!!.startsWith("account_v2_"))
        reply(JSONObject().put("accessToken",b).put("dataEnabled",true).put("accountId","1"))
        assertTrue(DataAuth.recover(c));assertNull(AvatarProfileStore.current.value);assertNull(ManualBodyStore.current.value)
        assertTrue(DataAuth.completeRestore());assertRestored()
        assertTrue(oldManual.all { manual.all[it.key]==it.value });assertTrue(oldAvatar.all { profiles.all[it.key]==it.value })
        resetAuth();DataAuth.initialize(app,vault);assertRestored()
        assertEquals(b,DataAuth.accessToken)
        assertFalse(app.getSharedPreferences("data_auth",0).all.values.any { it.toString().contains(b) })
    }

    @Test fun recoveryBeforeFirstStatusCapturesOnlyTheOldVerifiedOwner() = runBlocking {
        seedLegacy();status("1") // read-only preflight before rotation
        reply(JSONObject().put("accessToken",b).put("dataEnabled",true).put("accountId","1"))
        assertTrue(DataAuth.recover(c));assertTrue(DataAuth.completeRestore());assertRestored()
        assertEquals("/api/auth/status",server.takeRequest().path)
        assertEquals("/api/auth/recover",server.takeRequest().path)
        assertEquals(2,server.requestCount)
    }

    @Test fun differentRecoveryAccountNeverAdoptsPreviousOrGuestData() = runBlocking {
        seedLegacy()
        profiles.edit().putString("profile",AvatarProfileStore.encode(avatar.copy(weightKg=60f,centimeters=null,applied=emptyMap(),source=AvatarSource.BMI))).commit()
        status("1")
        reply(JSONObject().put("accessToken",b).put("dataEnabled",true).put("accountId","2"))
        assertTrue(DataAuth.recover(c));assertTrue(DataAuth.completeRestore());reload()
        assertNull(ManualBodyStore.current.value);assertNull(AvatarProfileStore.current.value)
        assertTrue(profiles.contains(BodyMeasurementScope.legacyScope(origin,a)+"profile"))
        assertTrue(profiles.contains("profile"))
    }

    @Test fun unprovableExpiredOldTokenIsRetainedButNotMovedIntoRecoveredAccount() = runBlocking {
        seedLegacy();reply(JSONObject().put("detail","authentication_required"),401)
        reply(JSONObject().put("accessToken",b).put("dataEnabled",true).put("accountId","2"))
        assertTrue(DataAuth.recover(c));assertTrue(DataAuth.completeRestore());reload()
        assertNull(AvatarProfileStore.current.value);assertNull(ManualBodyStore.current.value)
        assertTrue(profiles.contains(BodyMeasurementScope.legacyScope(origin,a)+"profile"))
    }

    @Test fun socialSwitchAndReturnKeepAccountsIsolatedAcrossDifferentTokens() = runBlocking {
        seedLegacy();status();assertTrue(DataAuth.validateSession());assertRestored()
        reply(JSONObject().put("switched",true).put("accessToken",b).put("accountId","2"))
        assertNotNull(DataAuth.socialLogin("/api/auth/google",JSONObject().put("idToken","fixture")))
        assertNull(AvatarProfileStore.current.value);assertTrue(DataAuth.completeRestore());reload()
        assertNull(ManualBodyStore.current.value);assertNull(AvatarProfileStore.current.value)
        val other=measurements.copy(waistCircumferenceCm=80f)
        assertTrue(ManualBodyStore.update(app,other,true))
        assertTrue(AvatarProfileStore.save(app,AvatarProfile(175f,75f)))
        reply(JSONObject().put("switched",true).put("accessToken",c).put("accountId","1"))
        assertNotNull(DataAuth.socialLogin("/api/auth/google",JSONObject().put("idToken","fixture")))
        assertTrue(DataAuth.completeRestore());assertRestored()
        assertEquals(80f,manual.getFloat(BodyMeasurementScope.accountScope(origin,"2")+"waist",0f),0f)
        DataAuth.markUnauthorized(c);assertNull(AvatarProfileStore.current.value);assertNull(ManualBodyStore.current.value)
    }

    @Test fun oldServerRemainsCompatibleWithoutInventingAnAccountIdentity() = runBlocking {
        seedLegacy();status(null);assertTrue(DataAuth.validateSession());assertRestored()
        assertEquals(BodyMeasurementScope.legacyScope(origin,a),BodyMeasurementScope.current())
        assertFalse(profiles.all.keys.any { it.startsWith("account_v2_") })
    }

    @Test fun guestRecordsAreNotAdoptedAndSigningOutDoesNotExposeAccountData() = runBlocking {
        seedLegacy();status();assertTrue(DataAuth.validateSession());assertRestored()
        assertTrue(AuthStore.continueAsGuest(app));assertNull(ManualBodyStore.current.value);assertNull(AvatarProfileStore.current.value)
        assertTrue(profiles.contains(BodyMeasurementScope.accountScope(origin,"1")+"profile"))
    }

    @Test fun migrationNeverOverwritesExistingDestinationOrChoosesAmbiguousHistory() {
        val p=app.getSharedPreferences("migration_test",0)
        val oldA=BodyMeasurementScope.legacyScope(origin,a);val oldB=BodyMeasurementScope.legacyScope(origin,b)
        val scope=BodyMeasurementScope.accountScope(origin,"1")
        p.edit().putString(oldA+"profile","A").putString(oldB+"profile","B").commit()
        assertFalse(BodyMeasurementScope.migrate(p,"migration_test",scope,listOf(oldA,oldB),listOf("profile")))
        assertFalse(p.contains(scope+"profile"))
        p.edit().putString(scope+"profile","newer").commit()
        assertTrue(BodyMeasurementScope.migrate(p,"migration_test",scope,listOf(oldA),listOf("profile")))
        assertEquals("newer",p.getString(scope+"profile",null));assertEquals("A",p.getString(oldA+"profile",null))
    }

    @Test fun failedMigrationCommitKeepsOriginalAndRetriesWithoutPublishingPartialCopy() {
        val p=app.getSharedPreferences("failed_migration",0)
        val old=BodyMeasurementScope.legacyScope(origin,a);val scope=BodyMeasurementScope.accountScope(origin,"1")
        p.edit().putString(old+"profile","original").commit()
        val failing=Proxy.newProxyInstance(javaClass.classLoader,arrayOf(SharedPreferences::class.java)) { _,method,args ->
            val result=method.invoke(p,*(args ?: emptyArray()))
            if(method.name=="edit") Proxy.newProxyInstance(javaClass.classLoader,arrayOf(SharedPreferences.Editor::class.java)) { proxy,m,a ->
                val value=m.invoke(result,*(a ?: emptyArray()))
                if(m.name=="commit") false else if(value is SharedPreferences.Editor) proxy else value
            } else result
        } as SharedPreferences
        assertFalse(BodyMeasurementScope.migrate(failing,"failed_migration",scope,listOf(old),listOf("profile")))
        assertEquals("original",p.getString(old+"profile",null));assertFalse(p.contains(scope+"profile"))
        assertTrue(BodyMeasurementScope.migrate(p,"failed_migration",scope,listOf(old),listOf("profile")))
        assertEquals("original",p.getString(scope+"profile",null));assertEquals("original",p.getString(old+"profile",null))
    }
}
