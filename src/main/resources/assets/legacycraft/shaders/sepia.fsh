#version 120
// Sepia: an old photograph.
uniform sampler2D scene;
uniform float strength;
varying vec2 uv;

void main() {
    vec3 c = texture2D(scene, uv).rgb;
    vec3 s = vec3(dot(c, vec3(0.393, 0.769, 0.189)), dot(c, vec3(0.349, 0.686, 0.168)), dot(c, vec3(0.272, 0.534, 0.131)));
    gl_FragColor = vec4(mix(c, clamp(s, 0.0, 1.0), strength), 1.0);
}
