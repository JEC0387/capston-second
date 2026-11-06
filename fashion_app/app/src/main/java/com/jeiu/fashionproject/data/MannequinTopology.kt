package com.jeiu.fashionproject.data

import kotlin.math.roundToInt
import java.util.concurrent.CancellationException

/** Detect detached sampled components. Connectivity alone does not prove anatomical accuracy. */
internal object MannequinTopology {
    fun connected(mesh:FloatArray,cancelled:()->Boolean):Boolean {
        require(mesh.isNotEmpty() && mesh.size%27==0)
        val parent=IntArray(mesh.size/9)
        val ids=HashMap<Long,Int>();var groups=0
        fun vertex(i:Int):Int {
            fun coordinate(v:Float):Long {
                require(v.isFinite())
                val n=(v*100000).roundToInt();require(n in -524288..524287)
                return (n+524288).toLong()
            }
            val key=(coordinate(mesh[i]) shl 40) or (coordinate(mesh[i+1]) shl 20) or coordinate(mesh[i+2])
            return ids.getOrPut(key) {val id=ids.size;parent[id]=-1;groups++;id}
        }
        fun root(v:Int):Int {var x=v;while(parent[x]>=0) {val p=parent[x];if(parent[p]>=0)parent[x]=parent[p];x=p};return x}
        fun union(a:Int,b:Int) {
            var x=root(a);var y=root(b);if(x==y)return
            if(parent[x]>parent[y]) {val t=x;x=y;y=t}
            parent[x]+=parent[y];parent[y]=x;groups--
        }
        for(i in mesh.indices.step(27)) {
            if(i%221184==0 && cancelled())throw CancellationException()
            val a=vertex(i);union(a,vertex(i+9));union(a,vertex(i+18))
        }
        return groups==1
    }
}
