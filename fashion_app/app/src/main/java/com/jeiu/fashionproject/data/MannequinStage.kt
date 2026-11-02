package com.jeiu.fashionproject.data

/** Analytic soft contact shadow, not a light-space body shadow map or cloth simulation. */
object MannequinStage {
    data class Geometry(val vertices:FloatArray,val height:Float,val footX:Float)
    fun build(body:AvatarCentimeters,mesh:FloatArray):Geometry {
        val h=body.heightCm/100f
        val y=mesh.indices.step(9).minOf {mesh[it+1]}-.002f*h
        val extent=maxOf(h*.48f,mesh.indices.step(9).maxOf {kotlin.math.abs(mesh[it])}+h*.12f)
        val coordinates=listOf(-extent to -h*.32f,extent to -h*.32f,extent to h*.42f,
            -extent to -h*.32f,extent to h*.42f,-extent to h*.42f)
        val vertices=coordinates.flatMap {(x,z)->listOf(x,y,z,0f,1f,0f,.9804f,.9725f,.9608f)}.toFloatArray()
        return Geometry(vertices,h,PremiumAvatarMesh.Shape(body).legX*.94f*h)
    }
}
