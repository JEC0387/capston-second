package com.jeiu.fashionproject.data

import kotlin.math.*

/** Original implicit mannequin, smooth unions + marching tetrahedra; no downloaded mesh/texture.
 * Coordinates are normalized by height during extraction, converted to metres for GLES.
 * The torso's named ellipse sections are the cm contract. They are illustrative depth assumptions.
 */
object PremiumAvatarMesh {
    const val STRIDE=9
    const val MAX_FLOATS=3_000_000 // 12MB maximum output; ordinary profiles stay below the P9 6MB bound.
    /** Derivative at the centre sample, including unequal distances to measurement rows.
     * Weighted secants avoid cancellation of large quadratic-interpolation coefficients.
     */
    internal fun rowDerivative(previous:Float,center:Float,next:Float,left:Float,right:Float):Float =
        ((center-previous)/left*right+(next-center)/right*left)/(left+right)
    data class Section(val y:Float,val a:Float,val b:Float)
    /** Monotone cubic interpolation preserves measured sections without shelves between them. */
    private data class Knot(val y:Float,val a:Float,val b:Float,val x:Float=0f,val z:Float=0f)
    private class Profile(val knots:List<Knot>) {
        private val channels=Array(4) {c->knots.map {when(c){0->it.a;1->it.b;2->it.x;else->it.z}}.toFloatArray()}
        private val slopes=Array(4) {c->
            val v=channels[c];val d=FloatArray(v.size-1) {i->(v[i+1]-v[i])/(knots[i+1].y-knots[i].y)}
            FloatArray(v.size) {i->when(i) {
                0->d.first();v.lastIndex->d.last()
                else->if(d[i-1]*d[i]<=0f)0f else {
                    val left=knots[i].y-knots[i-1].y;val right=knots[i+1].y-knots[i].y
                    val w1=2*right+left;val w2=right+2*left
                    (w1+w2)/(w1/d[i-1]+w2/d[i])
                }
            }}
        }
        init {require(knots.zipWithNext().all {(a,b)->b.y>a.y});require(knots.all {it.a>0&&it.b>0})}
        fun value(y:Float,c:Int):Float {
            val v=channels[c]
            if(y<=knots.first().y)return v.first()
            if(y>=knots.last().y)return v.last()
            var i=0;while(knots[i+1].y<y)i++
            val h=knots[i+1].y-knots[i].y;val t=(y-knots[i].y)/h;val t2=t*t;val t3=t2*t
            return (2*t3-3*t2+1)*v[i]+(t3-2*t2+t)*h*slopes[c][i]+(-2*t3+3*t2)*v[i+1]+(t3-t2)*h*slopes[c][i+1]
        }
    }
    private class Tube(a:Knot,b:Knot) {
        private val ax=a.x;private val ay=a.y;private val az=a.z
        private val ratio=(a.b/a.a+b.b/b.a)/2
        private val dx=b.x-a.x;private val dy=b.y-a.y;private val dz=(b.z-a.z)/ratio
        private val inv=1/(dx*dx+dy*dy+dz*dz)
        private val radius=a.a;private val delta=b.a-a.a
        fun field(x:Float,y:Float,z:Float):Float {
            val px=x-ax;val py=y-ay;val pz=(z-az)/ratio
            val t=((px*dx+py*dy+pz*dz)*inv).coerceIn(0f,1f)
            return sqrt((px-dx*t).pow(2)+(py-dy*t).pow(2)+(pz-dz*t).pow(2))-(radius+delta*t)
        }
    }
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
        val legX=max(hipR.a*.50f,thighR.a+.007f)
        private val headBottom=max(.865f,shoulder+.025f)
        private val headRadius=(1-headBottom)/2
        private val torso=Profile(listOf(
            Knot(hip-.080f,.018f,.022f,z=-.006f),Knot(hip-.058f,hipR.a*.64f,hipR.b*.70f,z=-.009f),
            Knot(hip-.028f,hipR.a*.94f,hipR.b*.96f,z=-.010f),Knot(hip,hipR.a,hipR.b,z=-.009f),
            Knot(waist,waistR.a,waistR.b,z=-.002f),Knot(chest,chestR.a,chestR.b,z=.006f),
            Knot(shoulder-.025f,body[AvatarPart.SHOULDER]/h*.45f,.059f,z=.002f),
            Knot(shoulder-.006f,body[AvatarPart.SHOULDER]/h*.32f,.045f),
            Knot(shoulder+.012f,.038f,.031f),Knot(headBottom+.016f,.026f,.028f,z=.002f)))
        private val knee=ankle+(hip-ankle)*.52f
        private val leg=Profile(listOf(
            Knot(.018f,thighR.a*.30f,thighR.a*.40f,legX*.94f,-.006f),
            Knot(ankle,thighR.a*.31f,thighR.a*.40f,legX*.94f,-.006f),
            Knot(ankle+(knee-ankle)*.67f,thighR.a*.52f,thighR.a*.64f,legX*.96f,-.009f),
            Knot(knee,thighR.a*.45f,thighR.a*.50f,legX*.98f,.006f),
            Knot(thigh,thighR.a,thighR.b,legX,-.003f),
            Knot(hip-.006f,thighR.a*.75f,thighR.b*.78f,min(legX,hipR.a*.48f),-.009f),
            Knot(hip+.020f,thighR.a*.25f,thighR.b*.30f,min(legX,hipR.a*.45f),-.009f)))
        private val rootX=body[AvatarPart.SHOULDER]/h*.48f
        private val upperX=max(rootX+.008f,chestR.a+.040f)
        private val wristY=shoulder-.30f
        private fun bodyEdge(y:Float):Float {
            val torsoEdge=if(y in torso.knots.first().y..torso.knots.last().y)torso.value(y,0) else 0f
            val legEdge=if(y in leg.knots.first().y..leg.knots.last().y)leg.value(y,2)+leg.value(y,0) else 0f
            return max(torsoEdge,legEdge)
        }
        private val clearance=run {
            val low=wristY-.075f;val dy=(shoulder-.080f-low)/40
            var slope=0f
            for(i in 0 until 40)slope=max(slope,abs(bodyEdge(low+(i+1)*dy)-bodyEdge(low+i*dy))/dy)
            (.012f*sqrt(1+slope.pow(2))).coerceAtMost(.060f)
        }
        private val arm=Profile(listOf(
            Knot(wristY-.067f,.010f,.010f,upperX-.003f,.010f),
            Knot(wristY-.048f,.014f,.010f,upperX-.003f,.010f),
            Knot(wristY-.030f,.016f,.011f,upperX-.003f,.010f),
            Knot(wristY-.013f,.016f,.011f,upperX-.002f,.010f),Knot(wristY,.012f,.014f,upperX,.006f),
            Knot(shoulder-.220f,.019f,.022f,upperX+.003f,-.002f),
            Knot(shoulder-.165f,.020f,.021f,upperX+.005f,-.007f),
            Knot(shoulder-.100f,.023f,.026f,upperX,.001f),
            Knot(shoulder-.045f,.024f,.027f,rootX+.005f,.002f),
            Knot(shoulder-.030f,.023f,.025f,rootX,0f)))
        private fun gap(y:Float):Float {
            val end=min(.085f,shoulder-chest-.006f)
            val t=((shoulder-y-.020f)/(end-.020f)).coerceIn(0f,1f)
            return -.025f+(.025f+clearance)*t*t*(3-2*t)
        }
        private val armTubes=run {
            val low=arm.knots.first().y;val high=arm.knots.last().y
            val rows=((0..24).map {low+(high-low)*it/24}+arm.knots.map {it.y}+listOf(chest,waist,hip,thigh).filter {it in low..high}).distinct().sorted()
            rows.map {y->val a=arm.value(y,0);Knot(y,a,arm.value(y,1),max(arm.value(y,2),bodyEdge(y)+a+gap(y)),arm.value(y,3))}
                .zipWithNext().map {(a,b)->Tube(a,b)}
        }
        val extentX=max(max(waistR.a,max(hipR.a,chestR.a))+.07f+clearance,max(rootX+.06f,legX+thighR.a+.06f+clearance))
        val extentZ=max(.110f,max(chestR.b,max(waistR.b,hipR.b))+.040f)
        fun section(part:AvatarPart):Section = when(part) {
            AvatarPart.CHEST->chestR;AvatarPart.WAIST->waistR;AvatarPart.HIP->hipR;AvatarPart.THIGH->thighR
            else->error("Only circumference sections")
        }
        private fun smooth(a:Float,b:Float,k:Float):Float {
            if(k<=0f)return min(a,b)
            val t=(.5f+.5f*(b-a)/k).coerceIn(0f,1f)
            return b+(a-b)*t-k*t*(1-t)
        }
        private fun sweep(x:Float,y:Float,z:Float,p:Profile):Float {
            val a=p.value(y,0);val b=p.value(y,1);val cx=p.value(y,2)
            val cz=p.value(y,3)
            val radial=(sqrt(((x-cx)/a).pow(2)+((z-cz)/b).pow(2))-1)*min(a,b)
            return max(radial,max(p.knots.first().y-y,y-p.knots.last().y))
        }
        private fun ellipsoid(x:Float,y:Float,z:Float,cx:Float,cy:Float,cz:Float,a:Float,b:Float,c:Float):Float =
            (sqrt(((x-cx)/a).pow(2)+((y-cy)/b).pow(2)+((z-cz)/c).pow(2))-1)*min(a,min(b,c))
        fun field(x:Float,y:Float,z:Float):Float {
            var d=sweep(x,y,z,torso)
            val distance=min(min(abs(y-chest),abs(y-waist)),min(abs(y-hip),abs(y-thigh)))
            val t=((distance-.012f)/.020f).coerceIn(0f,1f);val join=t*t*(3-2*t)
            val cranium=ellipsoid(x,y,z,0f,1-headRadius*.90f,.002f,.047f,headRadius*.90f,.051f)
            val jaw=ellipsoid(x,y,z,0f,headBottom+.033f,.010f,.034f,.038f,.037f)
            d=smooth(d,smooth(cranium,jaw,.010f),.010f)
            val sideX=abs(x)
            d=smooth(d,sweep(sideX,y,z,leg),.012f*join)
            val footX=legX*.94f
            val heel=ellipsoid(sideX,y,z,footX,.019f,-.012f,min(.018f,legX*.62f),.018f,.026f)
            val forefoot=ellipsoid(sideX,y,z,footX+.002f,.014f,.036f,min(.023f,legX*.72f),.014f,.047f)
            d=smooth(d,smooth(heel,forefoot,.009f),.008f)
            val deltoid=ellipsoid(sideX,y,z,rootX,shoulder-.040f,0f,.027f,.033f,.028f)
            var tube=Float.POSITIVE_INFINITY
            for(segment in armTubes)tube=min(tube,segment.field(sideX,y,z))
            if(y<shoulder-.080f)tube=max(tube,bodyEdge(y)+min(.025f,clearance)-sideX)
            val armD=smooth(tube,deltoid,.009f)
            d=smooth(d,armD,.010f*join)
            return d
        }
    }

    /** Fixed cell budget. Grid gradients provide continuous normals shared across tetrahedra. */
    fun build(body:AvatarCentimeters,resolution:Int=88,cancelled:()->Boolean={false}):FloatArray {
        require(body.issue()==null);require(resolution in 48..112)
        var current=resolution
        while(true) {
            val mesh=extract(body,current,cancelled)
            if(MannequinTopology.connected(mesh,cancelled))return mesh
            check(current<112) {"Mannequin sampling could not keep all limbs connected; saved measurements are unchanged"}
            current=min(112,current+16)
        }
    }
    private fun extract(body:AvatarCentimeters,resolution:Int,cancelled:()->Boolean):FloatArray {
        val shape=Shape(body)
        val step=1.04f/resolution
        val nx=ceil(2*shape.extentX/step).toInt().let {if(it%2==0)it else it+1} // include exact centre column
        val nz=ceil(2*shape.extentZ/step).toInt()
        val minX= -nx*step/2;val minY= -.020f;val minZ= -nz*step/2
        // Include the four requested section planes exactly, even between regular sampling rows.
        val sections=listOf(AvatarPart.CHEST,AvatarPart.WAIST,AvatarPart.HIP,AvatarPart.THIGH).map {shape.section(it).y}
        val ys=((0..resolution).map {minY+it*step}.filter {row->sections.none {abs(it-row)<.0001f}}+sections).sorted()
        val ny=ys.lastIndex
        val row=nx+1;val layer=row*(nz+1)
        fun index(x:Int,y:Int,z:Int)=y*layer+z*row+x
        val count=(ny+1)*layer
        val field=FloatArray(count);val gx=FloatArray(count);val gy=FloatArray(count);val gz=FloatArray(count)
        for(y in 0..ny) {
            if(cancelled())throw java.util.concurrent.CancellationException()
            for(z in 0..nz)for(x in 0..nx)field[index(x,y,z)]=shape.field(minX+x*step,ys[y],minZ+z*step)
        }
        for(y in 0..ny)for(z in 0..nz)for(x in 0..nx) {
            val i=index(x,y,z)
            gx[i]=(field[index(min(x+1,nx),y,z)]-field[index(max(x-1,0),y,z)])/(if(x==0||x==nx)step else 2*step)
            gy[i]=when(y) {
                0->(field[index(x,1,z)]-field[i])/(ys[1]-ys[0])
                ny->(field[i]-field[index(x,ny-1,z)])/(ys[ny]-ys[ny-1])
                else->rowDerivative(field[index(x,y-1,z)],field[i],field[index(x,y+1,z)],ys[y]-ys[y-1],ys[y+1]-ys[y])
            }
            gz[i]=(field[index(x,y,min(z+1,nz))]-field[index(x,y,max(z-1,0))])/(if(z==0||z==nz)step else 2*step)
        }
        var output=FloatArray(300_000);var used=0
        fun append(v:FloatArray) {
            check(used+9<=MAX_FLOATS) {"Mannequin mesh budget exceeded at grid $resolution"}
            if(used+9>output.size)output=output.copyOf(min(MAX_FLOATS,output.size*2))
            v.copyInto(output,used);used+=9
        }
        fun edge(a:Int,b:Int):FloatArray {
            val t=(field[a]/(field[a]-field[b])).coerceIn(0f,1f)
            fun coord(i:Int):FloatArray {val y=i/layer;val rest=i%layer;val z=rest/row;val x=rest%row
                return floatArrayOf(minX+x*step,ys[y],minZ+z*step)}
            val pa=coord(a);val pb=coord(b)
            val normal=floatArrayOf(gx[a]+(gx[b]-gx[a])*t,gy[a]+(gy[b]-gy[a])*t,gz[a]+(gz[b]-gz[a])*t)
            val length=sqrt(normal.sumOf {(it*it).toDouble()}.toFloat()).coerceAtLeast(.00001f)
            val h=body.heightCm/100f
            return floatArrayOf((pa[0]+(pb[0]-pa[0])*t)*h,(pa[1]+(pb[1]-pa[1])*t-.5f)*h,
                (pa[2]+(pb[2]-pa[2])*t)*h,normal[0]/length,normal[1]/length,normal[2]/length,.91f,.89f,.84f)
        }
        fun triangle(a:FloatArray,b:FloatArray,c:FloatArray) {
            val ux=b[0]-a[0];val uy=b[1]-a[1];val uz=b[2]-a[2]
            val vx=c[0]-a[0];val vy=c[1]-a[1];val vz=c[2]-a[2]
            val nxv=uy*vz-uz*vy;val nyv=uz*vx-ux*vz;val nzv=ux*vy-uy*vx
            // Keep valid thin triangles at exact measurement rows; removing them leaves tiny holes.
            if(nxv*nxv+nyv*nyv+nzv*nzv<1e-24f)return
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
        check(used>0 && used<MAX_FLOATS) {"Mannequin mesh budget exceeded ($used floats at grid $resolution)"}
        return output.copyOf(used)
    }
}
