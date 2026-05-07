package com.jelte.lightmare;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.GdxRuntimeException;

public class Shaders {
    public static String vertexShader = 
        "attribute vec4 vertex_positions;\n" +
        "attribute vec4 quad_colors;\n" +
        "attribute float s;\n" +
        "uniform mat4 u_projTrans;\n" +
        "varying vec4 v_color;\n" +
        "varying float v_s;\n" +
        "void main() {\n" +
        "   v_color = quad_colors;\n" +
        "   v_s = s;\n" +
        "   gl_Position = u_projTrans * vertex_positions;\n" +
        "}";

    public static String fragmentShader =
        "#ifdef GL_ES\n" +
        "precision mediump float;\n" +
        "#endif\n" +
        "varying vec4 v_color;\n" +
        "varying float v_s;\n" +
        "uniform vec2 u_resolution;\n" +
        "\n" +
        "float bayer2(float x, float y) {\n" +
        "    return 2.0*x + 3.0*y - 4.0*x*y;\n" +
        "}\n" +
        "\n" +
        "float bayer8(float x, float y) {\n" +
        "    return bayer2(mod(x, 2.0), mod(y, 2.0)) / 4.0\n" +
        "         + bayer2(mod(floor(x / 2.0), 2.0), mod(floor(y / 2.0), 2.0)) / 16.0\n" +
        "         + bayer2(floor(x / 4.0), floor(y / 4.0)) / 64.0;\n" +
        "}\n" +
        "\n" +
        "void main() {\n" +
        "    float brightness = v_s;\n" +
        "    vec2 virtualCoord = gl_FragCoord.xy * (vec2(1280.0, 720.0) / u_resolution);\n" +
        "    float x = mod(virtualCoord.x, 8.0);\n" +
        "    float y = mod(virtualCoord.y, 8.0);\n" +
        "    \n" +
        "    if (brightness < bayer8(x, y)) {\n" +
        "        discard;\n" +
        "    }\n" +
        "    \n" +
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
