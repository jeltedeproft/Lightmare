package com.jelte.lightmare;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.GdxRuntimeException;

public class Shaders {
    // Custom box2dlights shader: discards fragments where the smooth falloff `s`
    // is below a 4x4 Bayer threshold, producing a dithered cutoff on the light's
    // edge. Set on the RayHandler with setLightShader().
    public static String vertexShader =
        "attribute vec4 vertex_positions;\n" +
        "attribute vec4 quad_colors;\n" +
        "attribute float s;\n" +
        "uniform mat4 u_projTrans;\n" +
        "varying vec4 v_color;\n" +
        "varying float v_s;\n" +
        "void main() {\n" +
        "    v_color = quad_colors;\n" +
        "    v_s = s;\n" +
        "    gl_Position = u_projTrans * vertex_positions;\n" +
        "}";

    public static String fragmentShader =
        "#ifdef GL_ES\n" +
        "precision mediump float;\n" +
        "#endif\n" +
        "varying vec4 v_color;\n" +
        "varying float v_s;\n" +
        "\n" +
        "float bayer2(float x, float y) {\n" +
        "    return 2.0*x + 3.0*y - 4.0*x*y;\n" +
        "}\n" +
        "\n" +
        "float bayer4(float x, float y) {\n" +
        "    return bayer2(mod(x, 2.0), mod(y, 2.0)) / 4.0\n" +
        "         + bayer2(floor(x / 2.0), floor(y / 2.0)) / 16.0;\n" +
        "}\n" +
        "\n" +
        "void main() {\n" +
        "    // Inside the 640x360 FBO, gl_FragCoord is already in FBO pixels,\n" +
        "    // so the Bayer cell tiles directly on the world pixel grid.\n" +
        "    float threshold = bayer4(mod(floor(gl_FragCoord.x), 4.0),\n" +
        "                              mod(floor(gl_FragCoord.y), 4.0));\n" +
        "    if (v_s < threshold) discard;\n" +
        "    gl_FragColor = v_color;\n" +
        "}";

    public static ShaderProgram createDitherShader() {
        ShaderProgram shader = new ShaderProgram(vertexShader, fragmentShader);
        if (!shader.isCompiled()) {
            throw new GdxRuntimeException("Could not compile dither shader: " + shader.getLog());
        }
        return shader;
    }
}
