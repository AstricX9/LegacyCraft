#version 120
// Warm: a golden, late-afternoon tint.
uniform sampler2D scene;
uniform float strength;
varying vec2 uv;

void main() {
    vec3 c = texture2D(scene, uv).rgb;
    vec3 w = c * vec3(1.08, 1.0, 0.86) + vec3(0.02, 0.01, 0.0);
    gl_FragColor = vec4(mix(c, clamp(w, 0.0, 1.0), strength), 1.0);
}
