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
        "precision lowp float;\n" +
        "#endif\n" +
        "varying vec4 v_color;\n" +
        "varying float v_s;\n" +
        "uniform vec2 u_resolution;\n" +
        "\n" +
        "float get_bayer(int x, int y) {\n" +
        "    int index = x + y * 4;\n" +
        "    if (y == 0) {\n" +
        "        if (x == 0) return 0.0/16.0; if (x == 1) return 8.0/16.0; if (x == 2) return 2.0/16.0; return 10.0/16.0;\n" +
        "    } else if (y == 1) {\n" +
        "        if (x == 0) return 12.0/16.0; if (x == 1) return 4.0/16.0; if (x == 2) return 14.0/16.0; return 6.0/16.0;\n" +
        "    } else if (y == 2) {\n" +
        "        if (x == 0) return 3.0/16.0; if (x == 1) return 11.0/16.0; if (x == 2) return 1.0/16.0; return 9.0/16.0;\n" +
        "    } else {\n" +
        "        if (x == 0) return 15.0/16.0; if (x == 1) return 7.0/16.0; if (x == 2) return 13.0/16.0; return 5.0/16.0;\n" +
        "    }\n" +
        "}\n" +
        "\n" +
        "void main() {\n" +
        "    float brightness = v_s;\n" +
        "    // Align dithering to virtual pixels\n" +
        "    vec2 virtualCoord = gl_FragCoord.xy * (vec2(320.0, 180.0) / u_resolution);\n" +
        "    int x = int(mod(virtualCoord.x, 4.0));\n" +
        "    int y = int(mod(virtualCoord.y, 4.0));\n" +
        "    \n" +
        "    if (brightness < get_bayer(x, y)) {\n" +
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
