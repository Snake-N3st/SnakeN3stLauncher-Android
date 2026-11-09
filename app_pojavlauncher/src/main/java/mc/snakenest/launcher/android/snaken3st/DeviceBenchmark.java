package mc.snakenest.launcher.android.snaken3st;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Locale;

/** Fixed, short synthetic workloads. Neither result is a Minecraft FPS prediction. */
final class DeviceBenchmark {
    private static volatile int sink;
    private static final long SECOND = 1_000_000_000L;
    record Gpu(double millionPixels, double frameMillis, String renderer, boolean software) {}

    static double cpu() throws InterruptedException {
        cpuRun(SECOND / 2);
        return cpuRun(3 * SECOND);
    }
    private static double cpuRun(long duration) throws InterruptedException {
        long start=System.nanoTime(), iterations=0;
        int x=0x12345678, sum=0;
        do {
            checkCancelled();
            for(int i=0;i<32768;i++) {
                x^=x<<13;x^=x>>>17;x^=x<<5;x=x*1664525+1013904223;sum^=x;
            }
            iterations+=32768;
        } while(System.nanoTime()-start<duration);
        sink=sum;
        return iterations*1000.0/(System.nanoTime()-start);
    }
    static void checkCancelled() throws InterruptedException {
        if(Thread.currentThread().isInterrupted())throw new InterruptedException();
    }

    static Gpu gpu() throws InterruptedException {
        EGLDisplay display=EGL14.EGL_NO_DISPLAY;
        EGLContext context=EGL14.EGL_NO_CONTEXT;
        EGLSurface surface=EGL14.EGL_NO_SURFACE;
        int program=0;
        try {
            display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] version=new int[2];
            if(display==EGL14.EGL_NO_DISPLAY || !EGL14.eglInitialize(display,version,0,version,1))throw new IllegalStateException("EGL indisponible");
            EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
            int[] attrs={EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_NONE};
            if(!EGL14.eglChooseConfig(display,attrs,0,configs,0,1,count,0)||count[0]<1)throw new IllegalStateException("Configuration GLES 2 indisponible");
            context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
            surface=EGL14.eglCreatePbufferSurface(display,configs[0],new int[]{EGL14.EGL_WIDTH,512,EGL14.EGL_HEIGHT,512,EGL14.EGL_NONE},0);
            if(context==EGL14.EGL_NO_CONTEXT||surface==EGL14.EGL_NO_SURFACE||!EGL14.eglMakeCurrent(display,surface,surface,context))throw new IllegalStateException("Contexte GLES indisponible");
            String renderer=GLES20.glGetString(GLES20.GL_RENDERER);
            if(renderer==null)throw new IllegalStateException("GPU non identifié");
            String lower=renderer.toLowerCase(Locale.ROOT);
            boolean software=lower.contains("llvmpipe")||lower.contains("softpipe")||lower.contains("swiftshader")||lower.contains("software")||lower.contains("swrast");
            if(software)return new Gpu(0,0,renderer,true);
            String vertex="attribute vec2 aPosition;void main(){gl_Position=vec4(aPosition,0.0,1.0);}";
            String fragment="precision highp float;uniform float uSeed;void main(){vec3 p=vec3(gl_FragCoord.xy/512.0,uSeed);for(int i=0;i<48;i++){p=fract(sin(p.yzx*1.37+vec3(0.13,0.37,0.73))*4.31);}gl_FragColor=vec4(p,1.0);}";
            int vs=shader(GLES20.GL_VERTEX_SHADER,vertex),fs=0;
            try {
                fs=shader(GLES20.GL_FRAGMENT_SHADER,fragment);program=GLES20.glCreateProgram();
                GLES20.glAttachShader(program,vs);GLES20.glAttachShader(program,fs);GLES20.glBindAttribLocation(program,0,"aPosition");GLES20.glLinkProgram(program);
                int[] linked=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linked,0);
                if(linked[0]==0)throw new IllegalStateException("Shader non pris en charge : "+GLES20.glGetProgramInfoLog(program));
            } finally {GLES20.glDeleteShader(vs);if(fs!=0)GLES20.glDeleteShader(fs);}
            GLES20.glUseProgram(program);GLES20.glViewport(0,0,512,512);GLES20.glDisable(GLES20.GL_DITHER);
            FloatBuffer points=ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer();points.put(new float[]{-1,-1,1,-1,-1,1,1,1}).position(0);
            GLES20.glEnableVertexAttribArray(0);GLES20.glVertexAttribPointer(0,2,GLES20.GL_FLOAT,false,0,points);
            int seed=GLES20.glGetUniformLocation(program,"uSeed");
            frames(seed,SECOND/2);
            long start=System.nanoTime();int frames=frames(seed,4*SECOND);long elapsed=System.nanoTime()-start;
            return new Gpu(frames*512.0*512.0*1000/elapsed,elapsed/1_000_000.0/frames,renderer,false);
        } finally {
            if(display!=EGL14.EGL_NO_DISPLAY) {
                if(program!=0 && EGL14.eglGetCurrentContext()!=EGL14.EGL_NO_CONTEXT)GLES20.glDeleteProgram(program);
                EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
                if(surface!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,surface);
                if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);
                // EGLDisplay is process-wide: avoid eglTerminate invalidating the launcher's other contexts.
                EGL14.eglReleaseThread();
            }
        }
    }
    private static int shader(int type,String source) {
        int shader=GLES20.glCreateShader(type);GLES20.glShaderSource(shader,source);GLES20.glCompileShader(shader);
        int[] compiled=new int[1];GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,compiled,0);
        if(compiled[0]==0){String error=GLES20.glGetShaderInfoLog(shader);GLES20.glDeleteShader(shader);throw new IllegalStateException(error);}
        return shader;
    }
    private static int frames(int seed,long duration) throws InterruptedException {
        long start=System.nanoTime();int frames=0;
        do {
            checkCancelled();GLES20.glUniform1f(seed,(frames%101)/101.0f);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glFinish();
            if(GLES20.glGetError()!=GLES20.GL_NO_ERROR)throw new IllegalStateException("Échec de rendu GLES");
            frames++;
        } while(System.nanoTime()-start<duration);
        return frames;
    }
    static String report(double cpu,Gpu gpu,String error) {
        StringBuilder out=new StringBuilder(String.format(Locale.FRANCE,"CPU (un cœur) : %.1f millions d’itérations/s\n",cpu));
        out.append(cpu<40 ? "CPU limité pour les gros modpacks.\n" : (cpu<120 ? "CPU intermédiaire ; privilégier les packs légers.\n" : "CPU favorable aux modpacks plus exigeants, sous réserve de mémoire suffisante.\n"));
        if(gpu==null || gpu.software()) {
            out.append("GPU : mesure matérielle indisponible");
            if(gpu!=null)out.append(" (rendu logiciel : ").append(gpu.renderer()).append(")");
            else if(error!=null)out.append(" — ").append(error);
            out.append("\nAucun avis graphique fiable : vérifier le pilote et le moteur de rendu.");
        } else {
            out.append(String.format(Locale.FRANCE,"GPU : %s\n%.1f Mpixels/s · %.2f ms/image de test (512 × 512, 48 étapes shader)\n",gpu.renderer(),gpu.millionPixels(),gpu.frameMillis()));
            out.append("\nUsages indicatifs selon ce test :\n");
            usage(out,"Vanilla / petits modpacks",cpu>=40 && gpu.millionPixels()>=4);
            usage(out,"Modpacks moyens",cpu>=120 && gpu.millionPixels()>=15);
            usage(out,"Modpacks lourds",cpu>=240 && gpu.millionPixels()>=50);
            usage(out,"Shaders",cpu>=120 && gpu.millionPixels()>=50);
        }
        out.append("\nTest synthétique, seuils indicatifs non calibrés sur Minecraft : ces résultats ne prédisent pas les FPS. RAM, stockage, compatibilité des mods, pilote du jeu et chauffe ne sont pas évalués. Sur Android, le rendu natif GLES peut différer du moteur utilisé par Minecraft.");
        return out.toString();
    }
    private static void usage(StringBuilder out,String name,boolean suitable) {
        out.append(name).append(suitable ? " : à essayer, performances favorables\n" : " : déconseillé par ce test, réduire la charge\n");
    }
}
