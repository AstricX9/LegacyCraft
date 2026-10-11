#version 120
// Grayscale: black and white. Also a starting point for your own shader:
// copy it into the instance's shaders folder, edit it, and pick it as the
// Shaders addition's Custom File. It gets the picture as `scene`, the
// Strength setting (0-1) as `strength`, the seconds since the game started
// as `time`, the window size in pixels as `resolution`, and the position on
// screen (0-1) as `uv`.
uniform sampler2D scene;
uniform float strength;
uniform float time;
uniform vec2 resolution;
varying vec2 uv;

void main() {
    vec3 c = texture2D(scene, uv).rgb;
    vec3 g = vec3(dot(c, vec3(0.299, 0.587, 0.114)));
    gl_FragColor = vec4(mix(c, g, strength), 1.0);
}
