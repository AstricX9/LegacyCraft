// home.js: the homepage's world flyover and the "Site textures" picker.
(function(){
  // ---- site textures: try a tile from your own computer (the real ones are assets/dirt.png and assets/stone.png) ----
  var bar=document.querySelector('.bar');
  function pick(input,el){
    input.addEventListener('change',function(){
      var f=input.files&&input.files[0];if(!f)return;
      var url=URL.createObjectURL(f),img=new Image();
      img.onload=function(){ // scale any square-ish tile to ~48px with sharp pixels
        var scale=Math.max(1,Math.round(48/Math.max(img.width,img.height)));
        var size=(img.width*scale)+'px '+(img.height*scale)+'px';
        if(el===document.body){el.style.backgroundImage='var(--shade),url('+url+')';el.style.backgroundSize='auto,'+size}
        else{el.style.backgroundImage='url('+url+')';el.style.backgroundSize=size}
      };
      img.src=url;
    });
  }
  pick(document.getElementById('tileHeader'),bar);
  pick(document.getElementById('tileBg'),document.body);
  document.getElementById('tileReset').addEventListener('click',function(){
    [bar,document.body].forEach(function(el){el.style.backgroundImage='';el.style.backgroundSize=''});
    document.getElementById('tileHeader').value='';document.getElementById('tileBg').value='';
  });

  // ---- hero: blocky dusk flyover (heightmap ray-march) ----
  var cv=document.getElementById('world'),g=cv.getContext('2d'),W=cv.width,H=cv.height;
  var img=g.createImageData(W,H),px=img.data;
  function hash(x,y){var n=Math.sin(x*127.1+y*311.7)*43758.5453;return n-Math.floor(n)}
  function sm(x,y){var xi=Math.floor(x),yi=Math.floor(y),xf=x-xi,yf=y-yi;
    var a=hash(xi,yi),b=hash(xi+1,yi),c=hash(xi,yi+1),d=hash(xi+1,yi+1);
    var u=xf*xf*(3-2*xf),v=yf*yf*(3-2*yf);return a+(b-a)*u+(c-a)*v+(a-b-c+d)*u*v}
  var cache={};
  function cell(x,z){var k=x+','+z,v=cache[k];if(v)return v;
    var h=Math.floor(sm(x/14,z/14)*10+sm(x/5,z/5)*3)+62, sea=66, t=null;
    if(h>sea&&hash(x*3.1,z*7.7)>.8){h+=4;t='tree'}
    v=cache[k]={h:Math.max(h,sea),w:h<=sea,t:t,n:hash(x,z)};return v}
  var stars=[];for(var i=0;i<60;i++)stars.push([hash(i,1)*W|0,hash(i,2)*H*.45|0,hash(i,3)]);
  var cam={x:0,z:0,y:84,yaw:0.4},t0=0,running=false,reduce=matchMedia('(prefers-reduced-motion: reduce)').matches;
  function sky(y){var f=y/(H*.55);// top night blue -> red horizon
    return [12+f*70|0,12+f*14|0,26+f*6|0]}
  function draw(){
    var horizon=H*.45|0;
    for(var y=0;y<H;y++){var c=sky(Math.min(y,horizon+8));for(var x=0;x<W;x++){var o=(y*W+x)*4;px[o]=c[0];px[o+1]=c[1];px[o+2]=c[2];px[o+3]=255}}
    stars.forEach(function(s){var o=(s[1]*W+s[0])*4;var b=120+s[2]*100|0;px[o]=px[o+1]=px[o+2]=b});
    var sn=Math.sin(cam.yaw),cs=Math.cos(cam.yaw);
    for(var x=0;x<W;x++){
      var ray=(x/W-.5)*1.1, dx=sn+cs*ray, dz=cs-sn*ray, ybuf=H;
      for(var d=1;d<120;d+=d<30?.5:1){
        var wx=Math.floor(cam.x+dx*d),wz=Math.floor(cam.z+dz*d),c=cell(wx,wz);
        var sy=((cam.y-c.h)/d*90+horizon)|0; if(sy<0)sy=0;
        if(sy<ybuf){
          var r,gg,b;
          if(c.w){r=22;gg=38;b=92}else if(c.t){r=18;gg=46;b=20}else{r=40;gg=78;b=30}
          var sh=1-c.n*.15, fog=Math.min(1,d/110), fc=sky(horizon);
          // dusk light: dim everything, blend to horizon colour with distance
          r=r*sh*.55*(1-fog)+fc[0]*fog;gg=gg*sh*.55*(1-fog)+fc[1]*fog;b=b*sh*.55*(1-fog)+fc[2]*fog;
          for(var y=sy;y<ybuf;y++){var o=(y*W+x)*4,edge=(y===sy)?1.25:1;px[o]=r*edge;px[o+1]=gg*edge;px[o+2]=b*edge}
          ybuf=sy;
        }
      }
    }
    g.putImageData(img,0,0);
  }
  function tick(ts){if(!running)return;cam.z+=.12;cam.x+=.04;cam.yaw=0.4+Math.sin(ts/9000)*.15;draw();requestAnimationFrame(tick)}
  draw();
  document.getElementById('video').addEventListener('click',function(){
    if(reduce){cam.z+=8;draw();return}
    running=!running;this.classList.toggle('on',running);if(running)requestAnimationFrame(tick);
  });
})();
