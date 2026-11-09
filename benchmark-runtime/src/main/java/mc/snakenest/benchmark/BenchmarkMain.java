package mc.snakenest.benchmark;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.BufferUtils;
import java.io.*;
import java.nio.FloatBuffer;
import java.nio.file.*;
import java.util.*;

/** Runs inside the selected Amethyst Java runtime, never Android ART. */
public final class BenchmarkMain {
    private static volatile int sink;
    public static void main(String[] args) throws Exception {
        Properties p=new Properties();p.setProperty("translation",System.getProperty("sn3.benchmark.renderer","?"));p.setProperty("java",System.getProperty("java.runtime.version","?"));p.setProperty("vm",System.getProperty("java.vm.name","?"));p.setProperty("heap_mib",Long.toString(Runtime.getRuntime().maxMemory()/1048576));
        try {
            cpu(500000000L,null);p.setProperty("cpu",Double.toString(cpu(3000000000L,p)));
            gpu(p);
        } catch(Throwable e) {p.setProperty("error",e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage()));}
        Path target=Paths.get(args[0]),temp=Paths.get(args[0]+".part");
        try(OutputStream out=Files.newOutputStream(temp)){p.store(out,"SN3 benchmark JVM/LWJGL");}
        Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }
    private static double cpu(long duration,Properties p) {
        long start=System.nanoTime(),iterations=0,last=start,lastCount=0;StringBuilder samples=new StringBuilder();int x=0x12345678,sum=0;
        do {for(int i=0;i<32768;i++){x^=x<<13;x^=x>>>17;x^=x<<5;x=x*1664525+1013904223;sum^=x;}iterations+=32768;
            long now=System.nanoTime();if(p!=null&&now-last>=250000000L){if(samples.length()>0)samples.append(',');samples.append((iterations-lastCount)*1000.0/(now-last));last=now;lastCount=iterations;}
        }while(System.nanoTime()-start<duration);
        if(p!=null)p.setProperty("cpu_samples",samples.toString());
        sink=sum;return iterations*1000.0/(System.nanoTime()-start);
    }
    private static void gpu(Properties p) {
        long window=0;int program=0;
        try {
            if(!GLFW.glfwInit())throw new IllegalStateException("GLFW Amethyst indisponible");
            GLFW.glfwDefaultWindowHints();GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,2);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,1);
            window=GLFW.glfwCreateWindow(512,512,"Snake N3st Benchmark",0,0);
            if(window==0)throw new IllegalStateException("Contexte OpenGL via le moteur sélectionné indisponible");
            GLFW.glfwMakeContextCurrent(window);GLFW.glfwSwapInterval(0);GL.createCapabilities();
            String renderer=GL11.glGetString(GL11.GL_RENDERER);p.setProperty("renderer",String.valueOf(renderer));p.setProperty("vendor",String.valueOf(GL11.glGetString(GL11.GL_VENDOR)));p.setProperty("gl",String.valueOf(GL11.glGetString(GL11.GL_VERSION)));
            String lower=renderer==null?"":renderer.toLowerCase(Locale.ROOT);boolean software=lower.contains("llvmpipe")||lower.contains("softpipe")||lower.contains("swiftshader")||lower.contains("software")||lower.contains("swrast");p.setProperty("software",Boolean.toString(software));if(software)return;
            int vs=shader(GL20.GL_VERTEX_SHADER,"#version 120\nattribute vec2 aPosition;void main(){gl_Position=vec4(aPosition,0.0,1.0);}"),fs=0;
            try {
                fs=shader(GL20.GL_FRAGMENT_SHADER,"#version 120\nuniform float uSeed;void main(){vec3 p=vec3(gl_FragCoord.xy/512.0,uSeed);for(int i=0;i<48;i++){p=fract(sin(p.yzx*1.37+vec3(0.13,0.37,0.73))*4.31);}gl_FragColor=vec4(p,1.0);}");
                program=GL20.glCreateProgram();GL20.glAttachShader(program,vs);GL20.glAttachShader(program,fs);GL20.glBindAttribLocation(program,0,"aPosition");GL20.glLinkProgram(program);
                if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(program));
            } finally {GL20.glDeleteShader(vs);if(fs!=0)GL20.glDeleteShader(fs);}
            GL20.glUseProgram(program);GL11.glViewport(0,0,512,512);GL11.glDisable(GL11.GL_DITHER);
            FloatBuffer points=BufferUtils.createFloatBuffer(8);points.put(new float[]{-1,-1,1,-1,-1,1,1,1}).flip();GL20.glEnableVertexAttribArray(0);GL20.glVertexAttribPointer(0,2,GL11.GL_FLOAT,false,0,points);
            int seed=GL20.glGetUniformLocation(program,"uSeed");frames(seed,500000000L,null);long start=System.nanoTime();int frames=frames(seed,4000000000L,p);long elapsed=System.nanoTime()-start;
            GL11.glReadPixels(0,0,1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,BufferUtils.createByteBuffer(4));
            p.setProperty("gpu",Double.toString(frames*512.0*512.0*1000/elapsed));p.setProperty("frame_ms",Double.toString(elapsed/1000000.0/frames));
        } finally {if(program!=0)GL20.glDeleteProgram(program);if(window!=0)GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    private static int shader(int type,String source) {int shader=GL20.glCreateShader(type);GL20.glShaderSource(shader,source);GL20.glCompileShader(shader);if(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==0){String error=GL20.glGetShaderInfoLog(shader);GL20.glDeleteShader(shader);throw new IllegalStateException(error);}return shader;}
    private static int frames(int seed,long duration,Properties p) {long start=System.nanoTime(),last=start;int frames=0,lastFrames=0;StringBuilder samples=new StringBuilder();do {GL20.glUniform1f(seed,(frames%101)/101f);GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP,0,4);GL11.glFinish();if(GL11.glGetError()!=GL11.GL_NO_ERROR)throw new IllegalStateException("Erreur de rendu OpenGL");frames++;long now=System.nanoTime();if(p!=null&&now-last>=100000000L){if(samples.length()>0)samples.append(',');samples.append((now-last)/1000000.0/(frames-lastFrames));last=now;lastFrames=frames;}}while(System.nanoTime()-start<duration);if(p!=null)p.setProperty("gpu_samples",samples.toString());return frames;}
}
