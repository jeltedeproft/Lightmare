package com.jelte.lightmare;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.GdxRuntimeException;

public class Shaders {
    // Custom box2dlights shader: quantizes the smooth falloff `s` into three
    // concentric brightness rings and applies Bayer 4x4 dithering only inside
    // narrow transition zones around each ring boundary. Set on the RayHandler
    // via setLightShader().
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
        "    // Bayer threshold tied to FBO pixel grid (gl_FragCoord inside the\n" +
        "    // 640x360 FBO is in FBO pixels, so the dither snaps to world pixels).\n" +
        "    float bayer = bayer4(mod(floor(gl_FragCoord.x), 4.0),\n" +
        "                          mod(floor(gl_FragCoord.y), 4.0));\n" +
        "    \n" +
        "    // Narrow Bayer noise (width 0.08) added to s before quantising.\n" +
        "    // Far from a band boundary the noise is too small to flip the band,\n" +
        "    // so the rings stay flat-colored. Only pixels with s within ~0.04 of\n" +
        "    // a boundary get dithered.\n" +
        "    float ditherWidth = 0.08;\n" +
        "    float s_d = v_s + (bayer - 0.5) * ditherWidth;\n" +
        "    \n" +
        "    // Three rings. brightness scales the additive light contribution.\n" +
        "    float brightness;\n" +
        "    if (s_d < 0.333) {\n" +
        "        brightness = 0.15;   // outermost ring — barely-visible glow\n" +
        "    } else if (s_d < 0.667) {\n" +
        "        brightness = 0.45;   // middle ring\n" +
        "    } else {\n" +
        "        brightness = 1.0;    // innermost ring — full brightness\n" +
        "    }\n" +
        "    \n" +
        "    gl_FragColor = vec4(v_color.rgb, v_color.a * brightness);\n" +
        "}";

    public static ShaderProgram createDitherShader() {
        ShaderProgram shader = new ShaderProgram(vertexShader, fragmentShader);
        if (!shader.isCompiled()) {
            throw new GdxRuntimeException("Could not compile dither shader: " + shader.getLog());
        }
        return shader;
    }

    // ---------- House rectangle light ----------
    // Used when painting the house's footprint into the light FBO. Same 3-ring
    // brightness quantization + Bayer dither as the box2dlight shader above,
    // but with a rectangular falloff so the rings hug the house shape instead
    // of forming circles. Drawn through a regular SpriteBatch.

    public static String houseRectVertexShader =
        "attribute vec4 a_position;\n" +
        "attribute vec4 a_color;\n" +
        "attribute vec2 a_texCoord0;\n" +
        "uniform mat4 u_projTrans;\n" +
        "varying vec4 v_color;\n" +
        "varying vec2 v_texCoords;\n" +
        "void main() {\n" +
        "    v_color = a_color;\n" +
        "    v_texCoords = a_texCoord0;\n" +
        "    gl_Position = u_projTrans * a_position;\n" +
        "}";

    public static String houseRectFragmentShader =
        "#ifdef GL_ES\n" +
        "precision mediump float;\n" +
        "#endif\n" +
        "varying vec4 v_color;\n" +
        "varying vec2 v_texCoords;\n" +
        "uniform float u_padding;\n" +
        "uniform vec2 u_innerSize;\n" +
        "uniform vec2 u_quadSize;\n" +
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
        "    // Position within the quad in world pixels. The quad spans the\n" +
        "    // padded rect (inner rect inset by u_padding on every side).\n" +
        "    vec2 quadPos = v_texCoords * u_quadSize;\n" +
        "    vec2 relPos = quadPos - vec2(u_padding);\n" +
        "    // Chebyshev distance to the inner rect (0 inside, > 0 outside).\n" +
        "    // Using max-of-axes makes the falloff bands rectangular, mirroring\n" +
        "    // the house outline instead of forming circles.\n" +
        "    vec2 dxy = max(vec2(0.0), max(-relPos, relPos - u_innerSize));\n" +
        "    float dist = max(dxy.x, dxy.y);\n" +
        "    // s = 1.0 inside the rect, falls linearly to 0 at the padded edge.\n" +
        "    float s = max(0.0, 1.0 - dist / u_padding);\n" +
        "\n" +
        "    float bayer = bayer4(mod(floor(gl_FragCoord.x), 4.0),\n" +
        "                          mod(floor(gl_FragCoord.y), 4.0));\n" +
        "    float ditherWidth = 0.08;\n" +
        "    float s_d = s + (bayer - 0.5) * ditherWidth;\n" +
        "\n" +
        "    float brightness;\n" +
        "    if (s_d < 0.333) {\n" +
        "        brightness = 0.15;\n" +
        "    } else if (s_d < 0.667) {\n" +
        "        brightness = 0.45;\n" +
        "    } else {\n" +
        "        brightness = 1.0;\n" +
        "    }\n" +
        "\n" +
        "    gl_FragColor = vec4(v_color.rgb, v_color.a * brightness);\n" +
        "}";

    public static ShaderProgram createHouseRectShader() {
        ShaderProgram shader = new ShaderProgram(houseRectVertexShader, houseRectFragmentShader);
        if (!shader.isCompiled()) {
            throw new GdxRuntimeException("Could not compile house rect shader: " + shader.getLog());
        }
        return shader;
    }
}
