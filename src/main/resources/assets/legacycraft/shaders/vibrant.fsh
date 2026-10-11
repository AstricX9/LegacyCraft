#version 120
// Vibrant: richer colour and a little more contrast.
uniform sampler2D scene;
uniform float strength;
varying vec2 uv;

void main() {
    vec3 c = texture2D(scene, uv).rgb;
    float grey = dot(c, vec3(0.299, 0.587, 0.114));
    vec3 v = mix(vec3(grey), c, 1.35);
    v = (v - 0.5) * 1.08 + 0.5;
    gl_FragColor = vec4(mix(c, clamp(v, 0.0, 1.0), strength), 1.0);
}
