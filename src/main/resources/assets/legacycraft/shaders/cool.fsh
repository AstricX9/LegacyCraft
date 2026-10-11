#version 120
// Cool: a crisp blue tint.
uniform sampler2D scene;
uniform float strength;
varying vec2 uv;

void main() {
    vec3 c = texture2D(scene, uv).rgb;
    vec3 w = c * vec3(0.9, 0.98, 1.1);
    gl_FragColor = vec4(mix(c, clamp(w, 0.0, 1.0), strength), 1.0);
}
