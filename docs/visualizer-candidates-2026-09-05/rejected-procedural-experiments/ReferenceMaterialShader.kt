package com.codeframe78.twentyfourseven.player.ui

/** Original analytical materials. No source image sampling, recorded footage, or feedback history. */
internal val ReferenceMaterialShader = """
precision highp float;
uniform vec2 uResolution;
uniform float uTime;
uniform vec4 uAudio;
uniform int uStyle;
uniform float uGain;
const float PI=3.14159265;
float field(vec2 p) {
    return sin(p.x+sin(p.y*1.31))*.5+sin(p.y*1.73+cos(p.x*.81))*.3
        +sin(p.x*2.7-p.y*2.1)*.2;
}
vec3 spectrum(float x) { return .5+.5*cos(6.28318*(x+vec3(0.0,.33,.67))); }
float glow(float d,float width) { return width/(abs(d)+width); }
void main() {
    vec2 p=(gl_FragCoord.xy-.5*uResolution)/uResolution.y;
    float t=uTime, bass=uAudio.x, mid=uAudio.y, hi=uAudio.z, hit=uAudio.w;
    vec3 col=vec3(.002,.003,.012);
    if(uStyle==0) {
        // Reflective liquid membranes: broad shaded body, nested capillaries and hot rims.
        vec2 q=vec2(abs(p.x),p.y);
        float f=field(q*4.0+vec2(t*.17,-t*.23));
        float membrane=q.x-.43-f*(.22+bass*.12);
        float body=exp(-membrane*membrane*22.0);
        float ridge=sin(membrane*45.0+f*5.0);
        col+=vec3(.03,.30,.06)*body*(.35+.65*ridge*ridge);
        col+=vec3(.18,1.0,.34)*pow(glow(membrane,.014),2.0)*(1.0+hit);
        col+=vec3(.10,.65,.22)*pow(glow(sin(membrane*31.0+f*3.0),.055),2.0)*body;
        col+=vec3(.1,.25,.015)*pow(max(0.0,f),6.0)*body;
    } else if(uStyle==1) {
        // Translucent folded curtains in depth; occluded valleys and luminous vertical filaments.
        for(int i=0;i<6;i++) {
            float z=float(i)/6.0;
            float fold=field(vec2(p.x*3.2+t*.12+z*2.0,z*3.0-t*.07));
            float y=p.y+.12-fold*(.28+bass*.12)+z*.09;
            float curtain=exp(-y*y*(9.0+z*18.0));
            float threads=.65+.35*sin(p.x*95.0+fold*16.0+z*9.0);
            vec3 tint=mix(vec3(.02,.55,.40),vec3(.23,.05,.6),z);
            col+=tint*curtain*threads*(.23+hi*.12);
            col+=vec3(.2,.7,.8)*exp(-y*y*1800.0)*.18;
        }
    } else if(uStyle==2) {
        // Woven luminous fabric: displaced surface coordinates, thread interference, deep folds.
        vec2 q=p*vec2(1.0,1.5);
        float f=field(q*4.0+vec2(t*.2,-t*.08));
        q.y+=f*(.2+bass*.15); q.x+=sin(q.y*5.0+t*.2)*(.09+mid*.07);
        vec2 weave=abs(sin(q*vec2(240.0,180.0)));
        float fiber=pow(1.0-min(weave.x,weave.y),5.0);
        float light=.25+.75*pow(.5+.5*sin(q.x*8.0+f*2.0),2.0);
        col+=mix(vec3(.02,.08,.4),vec3(.45,.12,.75),.5+.5*f)*light*(.4+fiber);
        col+=vec3(.18,.5,.8)*pow((1.0-weave.x)*(1.0-weave.y),3.0)*light*(.7+hi);
    } else if(uStyle==3) {
        vec2 q=p; q.y*=1.35;
        float r=length(q), a=atan(q.y,q.x);
        float rings=r*(24.0-bass*2.0)+sin(a*3.0+t*.2)*.12;
        float cells=abs(sin(a*48.0+t*.4+floor(rings)*.4));
        float radial=abs(sin(rings*PI));
        float jewels=pow(glow(radial,.12)*glow(cells,.15),2.0);
        col+=mix(vec3(.55,.015,.32),vec3(.9,.45,.12),r)*jewels*(1.3+hit);
        col+=vec3(.3,.015,.22)*glow(radial,.035)*.4;
        col+=vec3(.6,.08,.28)*exp(-r*r*10.0)*.3;
    } else if(uStyle==4) {
        float r=length(p), a=atan(p.y,p.x);
        for(int i=0;i<5;i++) {
            float z=float(i), ang=a+t*(.035+z*.008)*(mod(z,2.0)*2.0-1.0)+z*.37;
            float petal=.12+z*.065+(.07+bass*.035)*cos(ang*(7.0+z));
            float d=r-petal;
            float surface=exp(-d*d*160.0)*(.5+.5*cos(ang*3.0+z));
            vec3 tint=spectrum(z*.16+ang*.12+t*.008);
            col+=tint*surface*.48;
            col+=mix(tint,vec3(1.0),.3)*pow(glow(d,.004),2.0)*(.35+hit*.4);
        }
        col+=vec3(.8,.5,.15)*exp(-r*r*350.0);
    } else if(uStyle==5) {
        // Filament families branch in polar space, with diffuse corona behind fine white-hot cores.
        float r=length(p), a=atan(p.y,p.x);
        for(int i=0;i<4;i++) {
            float z=float(i), n=sin(a*(11.0+z*7.0)+field(vec2(r*9.0-t*.25,a*2.0))*2.0);
            float filament=pow(glow(n,.018+hit*.015),2.0);
            col+=vec3(.05,.3,.8)*glow(n,.12)*.1/(.4+r);
            col+=vec3(.4,.7,1.0)*filament*(.35+hit)/(.5+r);
        }
        col+=vec3(.1,.3,.7)*exp(-r*r*60.0)*(.5+bass);
    } else if(uStyle==6) {
        float r=max(length(p),.002), a=atan(p.y,p.x);
        float depth=1.0/(r+.08);
        float lanes=sin(a*113.0+sin(a*31.0)*3.0);
        float travel=sin(depth*.8-t*(1.2+bass)+sin(a*57.0)*5.0);
        float streak=pow(glow(lanes,.055),2.0)*pow(max(0.0,travel),8.0);
        col+=mix(vec3(.15,.25,.9),vec3(1.0,.4,.055),.5+.5*sin(a*7.0))*streak*(1.0+hi);
        col+=vec3(.65,.22,.035)*exp(-r*5.0)*(.35+bass*.35);
        col+=vec3(1.0,.7,.3)*exp(-r*r*600.0)*(.6+hit);
    } else if(uStyle==7) {
        // Extruded chevron architecture: lit faces, dark recesses, emissive seams and fine scan texture.
        vec2 q=p*vec2(4.0,3.0); q.x+=t*.12;
        float row=floor(q.y), x=fract(q.x+row*.5)-.5;
        float d=fract(q.y)-abs(x)*1.3;
        float face=smoothstep(.1,.15,d)*(1.0-smoothstep(.64,.7,d));
        col+=vec3(.23,.015,.42)*face*(.25+.75*fract(q.y));
        col+=vec3(.85,.12,.6)*pow(glow(d-.13,.015),2.0)*(.6+hit);
        col+=vec3(.2,.07,.7)*glow(d-.65,.05)*.4;
        col+=vec3(.1,.6,.9)*pow(glow(d-.4-sin(t+row)*mid*.1,.006),2.0)*face*.5;
    } else if(uStyle==8) {
        vec2 q=p; float a=atan(q.y,q.x)+PI*.5;
        float sector=mod(a+PI/3.0,2.0*PI/3.0)-PI/3.0;
        float edge=max(length(q)*cos(sector),.008);
        float depth=-log(edge)*2.3+t*.24;
        float portal=abs(sin(depth*PI));
        float wall=.5+.5*cos(sector*3.0);
        vec3 tint=mix(vec3(.015,.45,.6),vec3(.7,.025,.35),.5+.5*sin(floor(depth)*2.0));
        col+=tint*(.09+.18*wall)*(.4+.6*edge);
        col+=tint*pow(glow(portal,.06+bass*.035),2.0)*(1.0+hit);
        col+=vec3(.25,.3,.6)*pow(glow(sin(sector*27.0),.035),2.0)*.2;
    } else {
        float r=max(length(p*vec2(1.0,1.3)),.01), a=atan(p.y*1.3,p.x);
        float depth=1.0/(r+.05)+t*.8;
        float lane=fract(a/6.28318*24.0);
        float tile=fract(depth*.7+floor(a/6.28318*24.0)*.17);
        vec3 tint=mix(vec3(.01,.2,.65),vec3(1.0,.3,.03),step(.7,fract(a*2.0)));
        float face=smoothstep(.08,.13,lane)*(1.0-smoothstep(.85,.92,lane));
        col+=tint*face*(.12+.4*tile)*smoothstep(.02,.3,r);
        col+=tint*pow(glow(tile-.85,.035),2.0)*face*(.5+hi);
        col+=vec3(.2,.6,.9)*pow(glow(lane-.08,.025),2.0)*.25;
    }
    float vignette=1.0-.3*smoothstep(.25,1.1,length(p));
    col=vec3(1.0)-exp(-col*vignette*1.6);
    gl_FragColor=vec4(pow(max(col,vec3(0.0)),vec3(.85))*uGain,1.0);
}
""".trimIndent()
