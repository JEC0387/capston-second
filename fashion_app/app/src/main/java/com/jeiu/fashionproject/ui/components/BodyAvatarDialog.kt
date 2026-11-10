package com.jeiu.fashionproject.ui.components

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jeiu.fashionproject.data.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Optional OpenGL ES 2.0 viewer; no asset download, network, body photo or persistence. */
@Composable
fun BodyAvatarDialog(profile: BodyDimensionProfile, configured: AvatarParameters? = null,
    thighScale: Float = 1f, onDismiss: () -> Unit) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var useProfile by remember { mutableStateOf(profile.values.isNotEmpty()) }
    var shoulder by remember { mutableFloatStateOf(1f) }
    var waist by remember { mutableFloatStateOf(1f) }
    var hip by remember { mutableFloatStateOf(1f) }
    var yaw by remember { mutableFloatStateOf(0f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var failed by remember { mutableStateOf(false) }
    var surface by remember { mutableStateOf<AvatarSurface?>(null) }
    val parameters=remember(profile,useProfile,configured) { configured ?: if(useProfile) AvatarParameters.from(profile) else AvatarParameters() }
    val supported=remember { (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
        ?.deviceConfigurationInfo?.reqGlEsVersion?.let { it>=0x20000 } == true }
    DisposableEffect(lifecycle,surface) {
        val observer=LifecycleEventObserver { _,event -> when(event) {
            Lifecycle.Event.ON_RESUME -> surface?.onResume()
            Lifecycle.Event.ON_PAUSE -> surface?.onPause()
            else -> Unit
        } }
        lifecycle.addObserver(observer)
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))surface?.onResume()
        onDispose { lifecycle.removeObserver(observer);surface?.onPause() }
    }
    AlertDialog(onDismissRequest=onDismiss,title={Text("3D 비율 아바타 · 참고 모델")},text={
        Column(Modifier.heightIn(max=560.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("${parameters.heightCm.toInt()} cm · ${if(configured != null) "선택한 출처의 표시 형상" else if(useProfile) "입력/사진 출처 반영" else "기본 모델"}")
            Text("실제 3D 표면을 회전해 보여줘요. 사진으로 복원한 몸이나 의류 시뮬레이션이 아니에요.")
            if(!supported || failed) Text("이 기기의 3D 화면을 준비하지 못했어요. 기존 체형 분석과 기록은 계속 사용할 수 있어요.")
            else AndroidView(factory={ctx -> AvatarSurface(ctx,{yaw=it},{zoom=it},{failed=true}).also { surface=it }},
                update={it.update(parameters,shoulder,waist,hip,yaw,zoom,thighScale)},modifier=Modifier.fillMaxWidth().height(300.dp))
            Text("드래그: 회전 · 두 손가락: 확대/축소")
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                TextButton(onClick={yaw=0f}) { Text("정면") }
                TextButton(onClick={yaw=90f}) { Text("측면") }
                TextButton(onClick={yaw=180f}) { Text("후면") }
            }
            OutlinedButton(onClick={yaw=0f;zoom=.8f}) {Text("모델 전체 보기 · 회전/줌 초기화")}
            Text("확대: ${"%.1f".format(zoom)}배");Slider(zoom,{zoom=it},valueRange=.8f..2f)
            if(parameters.centimeters==null) {
                Text("어깨 표시 비율");Slider(shoulder,{shoulder=it},valueRange=.8f..1.2f)
                Text("허리 표시 비율");Slider(waist,{waist=it},valueRange=.8f..1.2f)
                Text("엉덩이 표시 비율");Slider(hip,{hip=it},valueRange=.8f..1.2f)
            } else Text("cm 프로필의 부위 변경은 줄자 조절 화면에서 저장해요.")
            Text("표시 조절은 저장된 신체 치수나 사이즈 추천값을 변경하지 않아요. 관절 높이는 기본 인체의 표시 범위 안에서 제한해요.")
            parameters.sources.forEach { Text(it,style=MaterialTheme.typography.bodySmall) }
            parameters.notes.forEach { Text(it,style=MaterialTheme.typography.bodySmall) }
            OutlinedButton(enabled=configured == null && profile.values.isNotEmpty(),onClick={useProfile=!useProfile;shoulder=1f;waist=1f;hip=1f;yaw=0f;zoom=1f}) {
                Text(if(useProfile) "기본 모델로 되돌리기" else "내 치수 다시 반영")
            }
        }
    },confirmButton={TextButton(onClick=onDismiss){Text("3D 닫기")}})
}

/** Reuses the same real GLES mesh inside the fitting canvas. Touch rotates; pinch zooms. */
@Composable
fun BodyAvatarViewport(parameters: AvatarParameters, thighScale: Float, modifier: Modifier = Modifier,resetKey:Int=0,
    cameraYaw:Float?=null,onRotation:((Float)->Unit)?=null,fitModel:Boolean=false,allowTouch:Boolean=true,
    capture: AvatarFrameCapture? = null) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var yaw by remember { mutableFloatStateOf(0f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var failed by remember { mutableStateOf(false) }
    var surface by remember { mutableStateOf<AvatarSurface?>(null) }
    LaunchedEffect(resetKey) {yaw=0f;zoom=1f}
    val supported = remember { (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
        ?.deviceConfigurationInfo?.reqGlEsVersion?.let { it >= 0x20000 } == true }
    DisposableEffect(lifecycle, surface) {
        val observer = LifecycleEventObserver { _, event -> when (event) {
            Lifecycle.Event.ON_RESUME -> surface?.onResume()
            Lifecycle.Event.ON_PAUSE -> surface?.onPause()
            else -> Unit
        } }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) surface?.onResume()
        onDispose { lifecycle.removeObserver(observer); surface?.onPause() }
    }
    DisposableEffect(surface, capture) {
        val owner = surface
        capture?.reader = owner?.let { { it.captureFrame() } }
        onDispose { capture?.reader = null }
    }
    if (!supported || failed) {
        Text("이 기기에서 3D 모델을 표시하지 못했어요. 저장된 설정은 유지돼요.", modifier = modifier)
    } else {
        AndroidView(factory = { ctx -> AvatarSurface(ctx, { yaw = it;onRotation?.invoke(it) }, { zoom = it }, { failed = true }).also { surface = it } },
            update = { it.update(parameters, 1f, 1f, 1f, cameraYaw ?: yaw, zoom, thighScale,fitModel,allowTouch) }, modifier = modifier)
    }
}

private class AvatarSurface(context:Context,private val onYaw:(Float)->Unit,private val onZoom:(Float)->Unit,private val onError:()->Unit):GLSurfaceView(context) {
    private val avatar=AvatarRenderer { post { onError() } }
    private var lastX=0f
    private var yawValue=0f
    private var zoomValue=1f
    // Flat garment overlays remain front-facing. Disable GL touch rotation while
    // they are visible so the mannequin cannot turn without its 2D garments.
    private var touchEnabled=true
    private var geometry:List<Any>?=null
    private var renderedGeometry:List<Any>?=null
    private data class Request(val key:List<Any>,val p:AvatarParameters,val s:Float,val w:Float,val h:Float,val thigh:Float)
    private var scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private var requests=Channel<Request>(Channel.CONFLATED)
    @Volatile private var latest:Request?=null
    private fun startWorker() {
        scope.launch {
            for(request in requests) {
                try {
                    val mesh=request.p.centimeters?.let {PremiumAvatarMesh.build(it,cancelled={!isActive})}
                        ?: BodyAvatarMesh.build(request.p,request.s,request.w,request.h,request.thigh)
                    val stage=request.p.centimeters?.let {MannequinStage.build(it,mesh)}
                    if(isActive && latest==request)queueEvent {if(latest==request) {avatar.setGeometry(mesh,stage);renderedGeometry=request.key;requestRender()}}
                } catch(_:java.util.concurrent.CancellationException) {break}
                catch(_:IllegalArgumentException) {post {onError()}}
                catch(_:IllegalStateException) {post {onError()}}
            }
        }
    }
    private val pinch=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
        override fun onScale(detector:ScaleGestureDetector):Boolean {
            zoomValue=(zoomValue*detector.scaleFactor).coerceIn(.8f,2f);onZoom(zoomValue);return true
        }
    })
    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause=true
        setRenderer(avatar)
        renderMode=RENDERMODE_WHEN_DIRTY
        startWorker()
    }
    override fun onDetachedFromWindow() {scope.cancel();requests.close();super.onDetachedFromWindow()}
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if(!scope.isActive) {scope=CoroutineScope(SupervisorJob()+Dispatchers.Default);requests=Channel(Channel.CONFLATED);startWorker();latest?.let {requests.trySend(it)}}
    }
    fun update(parameters:AvatarParameters,shoulder:Float,waist:Float,hip:Float,yaw:Float,zoom:Float,thigh:Float = 1f,fit:Boolean=false,touchEnabled:Boolean=true) {
        this.touchEnabled=touchEnabled
        val key=listOf(parameters,shoulder,waist,hip,thigh)
        if(key!=geometry) {
            geometry=key
            Request(key,parameters,shoulder,waist,hip,thigh).also {latest=it;requests.trySend(it)}
        }
        yawValue=yaw;zoomValue=zoom
        queueEvent { avatar.yaw=yaw;avatar.zoom=zoom;avatar.distance=if(fit)parameters.heightCm/100f/.56f else 3.8f }
        requestRender()
    }
    suspend fun captureFrame(): android.graphics.Bitmap {
        // Ownership lives outside withTimeout: timeout can win while dispatching a completed frame.
        val owned = java.util.concurrent.atomic.AtomicReference<android.graphics.Bitmap?>(null)
        try {
            val captured = withTimeout(5000) {
                check(isAttachedToWindow && holder.surface.isValid && width > 0 && height > 0) { "avatar_surface_unavailable" }
                val expected = geometry ?: error("avatar_not_ready")
                val w = width; val h = height
                val result = CompletableDeferred<android.graphics.Bitmap>()
                try {
                    queueEvent {
                        if (result.isActive) try {
                            check(renderedGeometry == expected) { "avatar_geometry_pending" }
                            check(avatar.yaw == 0f && avatar.zoom == 1f) { "avatar_camera_pending" }
                            val bitmap = avatar.capture(w,h)
                            owned.set(bitmap)
                            if (!result.complete(bitmap)) owned.getAndSet(null)?.recycle()
                        } catch (_: OutOfMemoryError) { result.completeExceptionally(IllegalStateException("avatar_memory")) }
                        catch (failure: Exception) { result.completeExceptionally(failure) }
                    }
                    result.await()
                } finally { result.cancel() }
            }
            owned.compareAndSet(captured, null)
            return captured
        } finally { owned.getAndSet(null)?.recycle() }
    }
    override fun onTouchEvent(event:MotionEvent):Boolean {
        if(!touchEnabled)return false
        parent?.requestDisallowInterceptTouchEvent(true)
        pinch.onTouchEvent(event)
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> lastX=event.x
            MotionEvent.ACTION_MOVE -> { if(!pinch.isInProgress&&event.pointerCount==1) {
                yawValue=(yawValue+(event.x-lastX)*.5f+360f)%360f;onYaw(yawValue)
            };lastX=event.x }
            MotionEvent.ACTION_UP -> { performClick();parent?.requestDisallowInterceptTouchEvent(false) }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
    override fun performClick():Boolean { super.performClick();return true }
}

private class AvatarRenderer(private val onError:()->Unit):GLSurfaceView.Renderer {
    var yaw=0f;var zoom=1f
    var distance=3.8f
    private var surfaceWidth=0; private var surfaceHeight=0
    private var program=0
    private var failed=false
    private var count=0
    private var buffer:FloatBuffer?=null
    private var stageBuffer:FloatBuffer?=null
    private var stage:MannequinStage.Geometry?=null
    private var position=0;private var normal=0;private var color=0;private var mvpLocation=0;private var modelLocation=0
    private val projection=FloatArray(16);private val view=FloatArray(16);private val model=FloatArray(16)
    private val combined=FloatArray(16);private val mvp=FloatArray(16)
    fun setGeometry(data:FloatArray,stage:MannequinStage.Geometry?) {
        buffer=ByteBuffer.allocateDirect(data.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(data);position(0) }
        count=data.size/BodyAvatarMesh.STRIDE
        this.stage=stage
        stageBuffer=stage?.vertices?.let {vertices->ByteBuffer.allocateDirect(vertices.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {put(vertices);position(0)}}
    }
    private fun shader(type:Int,source:String):Int {
        val id=GLES20.glCreateShader(type);GLES20.glShaderSource(id,source);GLES20.glCompileShader(id)
        val status=IntArray(1);GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,status,0)
        check(status[0]!=0) { "Avatar shader unavailable" };return id
    }
    override fun onSurfaceCreated(gl:GL10?,config:EGLConfig?) {
        try {
            val vertex=shader(GLES20.GL_VERTEX_SHADER,"""
                uniform mat4 uMvp; uniform mat4 uModel;
                attribute vec3 aPosition; attribute vec3 aNormal; attribute vec3 aColor;
                varying mediump vec3 vColor; varying mediump vec3 vNormal; varying mediump vec3 vPosition; varying mediump vec3 vLocal;
                void main() {
                    vNormal=normalize(mat3(uModel)*aNormal);vPosition=(uModel*vec4(aPosition,1.0)).xyz;
                    vLocal=aPosition;vColor=aColor;gl_Position=uMvp*vec4(aPosition,1.0);
                }
            """.trimIndent())
            val fragment=shader(GLES20.GL_FRAGMENT_SHADER,"""
                precision mediump float; varying mediump vec3 vColor; varying mediump vec3 vNormal; varying mediump vec3 vPosition; varying mediump vec3 vLocal;
                uniform float uStageMode; uniform vec2 uStageSize; uniform float uEyeZ;
                void main(){
                    if(uStageMode>0.5){
                        vec2 p=vLocal.xz/uStageSize.x;
                        float foot=uStageSize.y/uStageSize.x;
                        vec2 a=(p-vec2(foot,0.018))/vec2(0.035,0.068);
                        vec2 b=(p-vec2(-foot,0.018))/vec2(0.035,0.068);
                        vec2 ambient=p/vec2(0.19,0.12);
                        float contact=max(exp(-dot(a,a)*1.5),exp(-dot(b,b)*1.5));
                        float shade=0.17*contact+0.045*exp(-dot(ambient,ambient));
                        gl_FragColor=vec4(vColor*(1.0-shade),1.0);return;
                    }
                    vec3 n=normalize(vNormal);vec3 key=normalize(vec3(-0.4,0.7,1.0));
                    float light=0.48+0.43*max(dot(n,key),0.0)+0.12*max(dot(n,normalize(vec3(0.8,0.15,0.5))),0.0)+0.06*max(n.y,0.0);
                    vec3 eye=normalize(vec3(0.0,0.1,uEyeZ)-vPosition);
                    float sheen=0.045*pow(max(dot(n,normalize(key+eye)),0.0),32.0);
                    if(uStageMode> -0.5){
                        light=0.50+0.38*max(dot(n,key),0.0)+0.10*max(dot(n,normalize(vec3(0.8,0.15,0.5))),0.0)+0.06*(n.y*0.5+0.5);
                        sheen=0.020*pow(max(dot(n,normalize(key+eye)),0.0),8.0);
                    }
                    gl_FragColor=vec4(vColor*light+vec3(sheen),1.0);
                }
            """.trimIndent())
            program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vertex);GLES20.glAttachShader(program,fragment);GLES20.glLinkProgram(program)
            val linked=IntArray(1);GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linked,0);check(linked[0]!=0)
            GLES20.glDeleteShader(vertex);GLES20.glDeleteShader(fragment)
            position=GLES20.glGetAttribLocation(program,"aPosition");normal=GLES20.glGetAttribLocation(program,"aNormal");color=GLES20.glGetAttribLocation(program,"aColor")
            mvpLocation=GLES20.glGetUniformLocation(program,"uMvp");modelLocation=GLES20.glGetUniformLocation(program,"uModel")
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glClearColor(.9804f,.9725f,.9608f,1f);failed=false
        } catch(_:RuntimeException) { failed=true;onError() }
    }
    override fun onSurfaceChanged(gl:GL10?,width:Int,height:Int) {
        surfaceWidth=width;surfaceHeight=height
        GLES20.glViewport(0,0,width,height)
        Matrix.perspectiveM(projection,0,38f,width.toFloat()/height.coerceAtLeast(1),.1f,10f)
    }
    /** Runs on the GLSurfaceView render thread: no Compose/SurfaceView screenshot assumptions. */
    fun capture(width:Int,height:Int): android.graphics.Bitmap {
        check(!failed && program != 0 && buffer != null && count > 0) { "avatar_not_ready" }
        require(width == surfaceWidth && height == surfaceHeight) { "avatar_surface_resizing" }
        require(width > 0 && height > 0 && width.toLong()*height <= 4_000_000)
        onDrawFrame(null)
        check(!failed)
        val rgba=ByteBuffer.allocateDirect(width*height*4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0,0,width,height,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,rgba)
        check(GLES20.glGetError()==GLES20.GL_NO_ERROR) { "avatar_read_failed" }
        val pixels=ByteArray(width*height*4);rgba.rewind();rgba.get(pixels)
        return AvatarFramePixels.bitmap(width,height,pixels)
    }
    override fun onDrawFrame(gl:GL10?) {
        if(failed)return
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val data=buffer?:return
        Matrix.setLookAtM(view,0,0f,0f,distance/zoom.coerceIn(.8f,2f),0f,0f,0f,0f,1f,0f)
        Matrix.setIdentityM(model,0);Matrix.rotateM(model,0,yaw,0f,1f,0f)
        Matrix.multiplyMM(combined,0,view,0,model,0);Matrix.multiplyMM(mvp,0,projection,0,combined,0)
        GLES20.glUseProgram(program);GLES20.glUniformMatrix4fv(mvpLocation,1,false,mvp,0);GLES20.glUniformMatrix4fv(modelLocation,1,false,model,0)
        val mode=GLES20.glGetUniformLocation(program,"uStageMode")
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"uEyeZ"),distance/zoom.coerceIn(.8f,2f))
        fun draw(vertices:FloatBuffer,verticesCount:Int) {
            listOf(position to 0,normal to 3,color to 6).forEach { (location,offset) ->
                vertices.position(offset);GLES20.glVertexAttribPointer(location,3,GLES20.GL_FLOAT,false,BodyAvatarMesh.STRIDE*4,vertices);GLES20.glEnableVertexAttribArray(location)
            }
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,verticesCount)
        }
        stageBuffer?.let {floor->stage?.let {s->
            GLES20.glUniform1f(mode,1f);GLES20.glUniform2f(GLES20.glGetUniformLocation(program,"uStageSize"),s.height,s.footX);draw(floor,6)
        }}
        GLES20.glUniform1f(mode,if(stage==null)-1f else 0f);draw(data,count)
        if(GLES20.glGetError()!=GLES20.GL_NO_ERROR) {failed=true;onError()}
    }
}
