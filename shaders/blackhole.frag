#define HIGHP

#define HALFPI 1.5707963267948966
#define MAX_UV_SHIFT 0.10

// NexBlackHole sẽ chèn "#define MAX_COUNT n" trước khi biên dịch; nếu không có thì dùng 16.
#ifndef MAX_COUNT
#define MAX_COUNT 16
#endif

uniform sampler2D u_texture;

uniform vec2 u_campos;
uniform vec2 u_resolution;

uniform int u_blackholecount;
uniform vec4 u_blackholes[MAX_COUNT];         // x, y, bán kính lõi, bán kính ngoài (toạ độ thế giới)
uniform vec4 u_blackholeStrengths[MAX_COUNT]; // x = độ mạnh

varying vec2 v_texCoords;

// Dựa theo shader lỗ đen của MEEPofFaith / WarHammer (TearingSpace.frag)
float interp(float a){
    float f = a - 1.0;
    float t = a;

    a = f * f * f * f * f + 1.0;           // pow5Out

    if(a <= 0.5){                          // circle
        a *= 2.0;
        a = (1.0 - sqrt(1.0 - a * a)) / 2.0;
    }else{
        a -= 1.0;
        a *= 2.0;
        a = (sqrt(1.0 - a * a) + 1.0) / 2.0;
    }
    return f + (t - f) * a;
}

void main(){
    vec2 uv = v_texCoords.xy;
    vec2 coords = (uv * u_resolution) + u_campos;
    vec2 offset = vec2(0.0);

    for(int i = 0; i < MAX_COUNT; i++){
        if(i >= u_blackholecount) break;

        vec4 hole = u_blackholes[i];
        float strength = u_blackholeStrengths[i].x;
        if(strength <= 0.0001) continue;

        float iR = hole.z;
        float oR = hole.w;
        float dst = distance(hole.xy, coords);

        if(dst > oR || dst <= iR) continue;   // ngoài vùng ảnh hưởng, hoặc bên trong lõi

        float p = interp(clamp((dst - iR) / max(oR - iR, 0.0001), 0.0, 1.0));
        float a = atan(coords.x - hole.x, coords.y - hole.y) + HALFPI;
        vec2 pos = vec2(hole.x - oR * cos(a) * p, hole.y + oR * sin(a) * p);
        offset += (pos - coords) * strength;
    }

    vec2 maxShift = u_resolution * MAX_UV_SHIFT;
    offset = clamp(offset, -maxShift, maxShift);

    vec2 sampleUv = clamp((coords + offset - u_campos) / u_resolution, 0.0, 1.0);
    gl_FragColor = texture2D(u_texture, sampleUv);
}
