package com.jeiu.fashionproject

import kotlin.math.*
import com.jeiu.fashionproject.data.*

/** Original implicit mannequin, smooth unions + marching tetrahedra; no downloaded mesh/texture.
 * Coordinates are normalized by height during extraction, converted to metres for GLES.
 * The torso's named ellipse sections are the cm contract. They are illustrative depth assumptions.
 */
internal object P9MannequinReference {
    const val STRIDE=9
    data class Section(val y:Float,val a:Float,val b:Float)
    class Shape(val body:AvatarCentimeters) {
        val h=body.heightCm
        val ankle=.04f
        val hip=body[AvatarPart.LEG]/h+ankle
        val shoulder=hip+body[AvatarPart.TORSO]/h
        val waist=hip+(shoulder-hip)*.40f
        val chest=max(waist+.025f,shoulder-.095f).coerceAtMost(shoulder-.060f)
        val thigh=hip-.09f
        private fun section(part:AvatarPart,y:Float)=body.radii(part).let {Section(y,it.first/h,it.second/h)}
        private val hipR=section(AvatarPart.HIP,hip)
        private val waistR=section(AvatarPart.WAIST,waist)
        private val chestR=section(AvatarPart.CHEST,chest)
        private val thighR=section(AvatarPart.THIGH,thigh)
        val legX=max(hipR.a*.56f,thighR.a+min(.008f,hipR.a*.12f))
        private val headBottom=max(.865f,shoulder+.025f)
        private val headRadius=(1-headBottom)/2
        private val headCenter=1-headRadius
        private val torso=listOf(Section(hip-.075f,.007f,.010f),Section(hip-.050f,hipR.a*.55f,hipR.b*.72f),
            Section(hip-.020f,hipR.a*.96f,hipR.b*.97f),hipR,waistR,chestR,
            Section(shoulder-.020f,body[AvatarPart.SHOULDER]/h*.42f,.066f),
            Section(shoulder+.005f,body[AvatarPart.SHOULDER]/h*.28f,.048f),
            Section(shoulder+.028f,.032f,.030f),Section(headBottom+.018f,.030f,.030f))
        private val knee=ankle+(hip-ankle)*.52f
        private val leg=listOf(Section(.018f,thighR.a*.43f,thighR.a*.52f),Section(ankle,thighR.a*.40f,thighR.a*.49f),
            Section(ankle+(knee-ankle)*.48f,thighR.a*.67f,thighR.a*.75f),Section(knee,thighR.a*.53f,thighR.a*.59f),
            thighR,Section(hip-.020f,thighR.a*.65f,thighR.b*.65f),Section(hip+.025f,thighR.a*.12f,thighR.b*.12f))
        private val rootX=body[AvatarPart.SHOULDER]/h/2-.026f
        private val upperX=max(rootX+.050f,chestR.a+.063f)
        private val elbowX=max(upperX+.010f,max(waistR.a+.045f,hipR.a+.055f))
        private val wristX=max(elbowX+.003f,hipR.a+.043f)
        private val upperY=shoulder-.060f;private val elbowY=shoulder-.17f;private val wristY=shoulder-.345f
        val extentX=max(wristX+.045f,max(hipR.a,thighR.a+legX)+.025f)
        val extentZ=max(.105f,max(chestR.b,max(waistR.b,hipR.b))+.025f)
        fun section(part:AvatarPart):Section = when(part) {
            AvatarPart.CHEST->chestR;AvatarPart.WAIST->waistR;AvatarPart.HIP->hipR;AvatarPart.THIGH->thighR
            else->error("Only circumference sections")
        }
        private fun smooth(a:Float,b:Float,k:Float):Float {
            if(k<=0f)return min(a,b)
            val t=(.5f+.5f*(b-a)/k).coerceIn(0f,1f)
            return b+(a-b)*t-k*t*(1-t)
        }
        private fun radii(y:Float,sections:List<Section>):Pair<Float,Float> {
            if(y<=sections.first().y)return sections.first().a to sections.first().b
            if(y>=sections.last().y)return sections.last().a to sections.last().b
            val i=sections.indexOfFirst {it.y>=y};val a=sections[i-1];val b=sections[i]
            val t=((y-a.y)/(b.y-a.y)).coerceIn(0f,1f);val u=t*t*(3-2*t)
            return (a.a+(b.a-a.a)*u) to (a.b+(b.b-a.b)*u)
        }
        private fun sweep(x:Float,y:Float,z:Float,sections:List<Section>,cx:Float=0f):Float {
            val (a,b)=radii(y,sections)
            val radial=(sqrt(((x-cx)/a).pow(2)+(z/b).pow(2))-1)*min(a,b)
            return max(radial,max(sections.first().y-y,y-sections.last().y))
        }
        private fun ellipsoid(x:Float,y:Float,z:Float,cx:Float,cy:Float,cz:Float,a:Float,b:Float,c:Float):Float =
            (sqrt(((x-cx)/a).pow(2)+((y-cy)/b).pow(2)+((z-cz)/c).pow(2))-1)*min(a,min(b,c))
        private fun capsule(x:Float,y:Float,z:Float,ax:Float,ay:Float,az:Float,bx:Float,by:Float,bz:Float,r0:Float,r1:Float):Float {
            val dx=bx-ax;val dy=by-ay;val dz=bz-az
            val t=(((x-ax)*dx+(y-ay)*dy+(z-az)*dz)/(dx*dx+dy*dy+dz*dz)).coerceIn(0f,1f)
            return sqrt((x-ax-dx*t).pow(2)+(y-ay-dy*t).pow(2)+(z-az-dz*t).pow(2))-(r0+(r1-r0)*t)
        }
        fun field(x:Float,y:Float,z:Float):Float {
            var d=sweep(x,y,z,torso)
            // Keep named measurement bands free of union inflation. Blend smoothly between bands.
            val distance=min(abs(y-chest),min(abs(y-waist),abs(y-hip)))
            val t=((distance-.020f)/.020f).coerceIn(0f,1f)
            val join=t*t*(3-2*t)
            // Chin and cranium are blended into a continuous, featureless head/neck surface.
            val cranium=ellipsoid(x,y,z,0f,1-headRadius*.90f,.001f,.049f,headRadius*.90f,.052f)
            val jaw=ellipsoid(x,y,z,0f,headBottom+.032f,.008f,.036f,.040f,.040f)
            val head=smooth(cranium,jaw,.012f)
            d=smooth(d,head,.012f)
            for(sideIndex in 0..1) {
                val side=if(sideIndex==0)-1f else 1f
                // Legs remain separate below the crotch; smooth only at the pelvis attachment.
                val legD=sweep(x,y,z,leg,side*legX)
                d=if(y>hip-.055f)smooth(d,legD,.014f*join) else min(d,legD)
                val foot=ellipsoid(x,y,z,side*legX,.023f,.028f,.029f,.023f,.065f)
                d=smooth(d,foot,.008f)
                var arm=capsule(x,y,z,side*rootX,shoulder-.010f,0f,side*upperX,upperY,0f,.026f,.030f)
                arm=smooth(arm,capsule(x,y,z,side*upperX,upperY,0f,side*elbowX,elbowY,0f,.030f,.024f),.008f)
                arm=smooth(arm,capsule(x,y,z,side*elbowX,elbowY,0f,side*wristX,wristY,0f,.024f,.016f),.008f)
                val hand=ellipsoid(x,y,z,side*(wristX+.003f),wristY-.023f,.002f,.020f,.033f,.012f)
                arm=smooth(arm,hand,.007f)
                d=smooth(d,arm,.012f*join)
            }
            return d
        }
    }

    /** Fixed cell budget. Grid gradients provide continuous normals shared across tetrahedra. */
    fun build(body:AvatarCentimeters,resolution:Int=88,cancelled:()->Boolean={false}):FloatArray {
        require(body.issue()==null);require(resolution in 48..112)
        val shape=Shape(body)
        val step=1.04f/resolution
        val nx=ceil(2*shape.extentX/step).toInt().let {if(it%2==0)it else it+1} // include exact centre column
        val nz=ceil(2*shape.extentZ/step).toInt();val ny=resolution
        val minX= -nx*step/2;val minY= -.020f;val minZ= -nz*step/2
        val row=nx+1;val layer=row*(nz+1)
        fun index(x:Int,y:Int,z:Int)=y*layer+z*row+x
        val count=(ny+1)*layer
        val field=FloatArray(count);val gx=FloatArray(count);val gy=FloatArray(count);val gz=FloatArray(count)
        for(y in 0..ny) {
            if(cancelled())throw java.util.concurrent.CancellationException()
            for(z in 0..nz)for(x in 0..nx)field[index(x,y,z)]=shape.field(minX+x*step,minY+y*step,minZ+z*step)
        }
        for(y in 0..ny)for(z in 0..nz)for(x in 0..nx) {
            val i=index(x,y,z)
            gx[i]=(field[index(min(x+1,nx),y,z)]-field[index(max(x-1,0),y,z)])/(if(x==0||x==nx)step else 2*step)
            gy[i]=(field[index(x,min(y+1,ny),z)]-field[index(x,max(y-1,0),z)])/(if(y==0||y==ny)step else 2*step)
            gz[i]=(field[index(x,y,min(z+1,nz))]-field[index(x,y,max(z-1,0))])/(if(z==0||z==nz)step else 2*step)
        }
        var output=FloatArray(300_000);var used=0
        fun append(v:FloatArray) {
            if(used+9>output.size)output=output.copyOf(output.size*2)
            v.copyInto(output,used);used+=9
        }
        fun edge(a:Int,b:Int):FloatArray {
            val t=(field[a]/(field[a]-field[b])).coerceIn(0f,1f)
            fun coord(i:Int):FloatArray {val y=i/layer;val rest=i%layer;val z=rest/row;val x=rest%row
                return floatArrayOf(minX+x*step,minY+y*step,minZ+z*step)}
            val pa=coord(a);val pb=coord(b)
            val normal=floatArrayOf(gx[a]+(gx[b]-gx[a])*t,gy[a]+(gy[b]-gy[a])*t,gz[a]+(gz[b]-gz[a])*t)
            val length=sqrt(normal.sumOf {(it*it).toDouble()}.toFloat()).coerceAtLeast(.00001f)
            val h=body.heightCm/100f
            return floatArrayOf((pa[0]+(pb[0]-pa[0])*t)*h,(pa[1]+(pb[1]-pa[1])*t-.5f)*h,
                (pa[2]+(pb[2]-pa[2])*t)*h,normal[0]/length,normal[1]/length,normal[2]/length,.89f,.86f,.80f)
        }
        fun triangle(a:FloatArray,b:FloatArray,c:FloatArray) {
            val ux=b[0]-a[0];val uy=b[1]-a[1];val uz=b[2]-a[2]
            val vx=c[0]-a[0];val vy=c[1]-a[1];val vz=c[2]-a[2]
            val nxv=uy*vz-uz*vy;val nyv=uz*vx-ux*vz;val nzv=ux*vy-uy*vx
            if(nxv*nxv+nyv*nyv+nzv*nzv<1e-16f)return
            val dot=nxv*(a[3]+b[3]+c[3])+nyv*(a[4]+b[4]+c[4])+nzv*(a[5]+b[5]+c[5])
            append(a);if(dot>=0) {append(b);append(c)} else {append(c);append(b)}
        }
        val tetra=arrayOf(intArrayOf(0,5,1,6),intArrayOf(0,1,2,6),intArrayOf(0,2,3,6),
            intArrayOf(0,3,7,6),intArrayOf(0,7,4,6),intArrayOf(0,4,5,6))
        for(y in 0 until ny) {
            if(cancelled())throw java.util.concurrent.CancellationException()
            for(z in 0 until nz)for(x in 0 until nx) {
                val cube=intArrayOf(index(x,y,z),index(x+1,y,z),index(x+1,y+1,z),index(x,y+1,z),
                    index(x,y,z+1),index(x+1,y,z+1),index(x+1,y+1,z+1),index(x,y+1,z+1))
                if(cube.all {field[it]>=0}||cube.all {field[it]<0})continue
                for(t in tetra) {
                    val inside=t.map {cube[it]}.filter {field[it]<0};val outside=t.map {cube[it]}.filter {field[it]>=0}
                    if(inside.isEmpty()||outside.isEmpty())continue
                    if(inside.size==1)triangle(edge(inside[0],outside[0]),edge(inside[0],outside[1]),edge(inside[0],outside[2]))
                    else if(inside.size==3)triangle(edge(outside[0],inside[0]),edge(outside[0],inside[1]),edge(outside[0],inside[2]))
                    else {
                        val a=edge(inside[0],outside[0]);val b=edge(inside[0],outside[1])
                        val c=edge(inside[1],outside[0]);val d=edge(inside[1],outside[1])
                        triangle(a,b,c);triangle(b,d,c)
                    }
                }
            }
        }
        check(used>0 && used<1_500_000) {"Mannequin mesh budget exceeded"}
        return output.copyOf(used)
    }
}
