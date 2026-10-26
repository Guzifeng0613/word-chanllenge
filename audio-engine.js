/* ============================================================
 * V4.8 音频引擎：程序化合成音效 + 音序器 BGM + 震动
 * 全部用 Web Audio API 实时合成，无外部音频文件，可离线、零体积
 * ============================================================ */
const AUDIO_KEY='word_dungeon_audio_v1';
let AUD={sfx:true,bgm:true,vibrate:true,sfxVol:0.85,bgmVol:0.5};
function loadAudioSettings(){ try{Object.assign(AUD,JSON.parse(localStorage.getItem(AUDIO_KEY)||'{}'));}catch(e){} }
function saveAudioSettings(){ try{localStorage.setItem(AUDIO_KEY,JSON.stringify(AUD));}catch(e){} }
loadAudioSettings();

const AudioEngine=(()=>{
  let ctx=null,sfxBus=null,bgmBus=null;
  let schedTimer=null,bgmStep=0,nextStepTime=0,bgmMode='home';

  function ensure(){
    if(!ctx){
      const AC=window.AudioContext||window.webkitAudioContext;
      if(!AC) return null;
      ctx=new AC();
      sfxBus=ctx.createGain(); sfxBus.connect(ctx.destination);
      bgmBus=ctx.createGain(); bgmBus.connect(ctx.destination);
      applyVol();
    }
    if(ctx.state==='suspended') ctx.resume();
    return ctx;
  }
  function unlock(){ ensure(); }
  function applyVol(){
    if(!ctx) return;
    sfxBus.gain.value=AUD.sfx?AUD.sfxVol:0;
    bgmBus.gain.value=AUD.bgm?AUD.bgmVol:0;
  }

  /* ── 基础合成单元 ── */
  function tone(freq,start,dur,type,vol,slideTo,bus){
    if(!ensure()) return;
    const t0=ctx.currentTime+(start||0);
    const o=ctx.createOscillator(),g=ctx.createGain();
    o.type=type||'sine'; o.frequency.setValueAtTime(freq,t0);
    if(slideTo) o.frequency.exponentialRampToValueAtTime(Math.max(20,slideTo),t0+dur);
    g.gain.setValueAtTime(0.0001,t0);
    g.gain.linearRampToValueAtTime(vol,t0+0.008);
    g.gain.exponentialRampToValueAtTime(0.0008,t0+dur);
    o.connect(g); g.connect(bus||sfxBus);
    o.start(t0); o.stop(t0+dur+0.05);
  }
  function noise(start,dur,vol,filterFreq,ftype,q){
    if(!ensure()) return;
    const t0=ctx.currentTime+(start||0);
    const n=Math.floor(ctx.sampleRate*dur);
    const buf=ctx.createBuffer(1,n,ctx.sampleRate);
    const d=buf.getChannelData(0);
    for(let i=0;i<n;i++) d[i]=Math.random()*2-1;
    const src=ctx.createBufferSource(); src.buffer=buf;
    const f=ctx.createBiquadFilter(); f.type=ftype||'lowpass';
    f.frequency.value=filterFreq||2000; f.Q.value=q||0.7;
    const g=ctx.createGain();
    g.gain.setValueAtTime(vol,t0);
    g.gain.exponentialRampToValueAtTime(0.0008,t0+dur);
    src.connect(f); f.connect(g); g.connect(sfxBus);
    src.start(t0); src.stop(t0+dur);
  }

  /* ── 音效清单 ── */
  const SFX={
    hit(){ tone(240,0,0.14,'triangle',0.45,70); noise(0,0.07,0.22,2600); },
    crit(){ tone(320,0,0.3,'square',0.32,60); noise(0,0.18,0.3,1800); tone(660,0.02,0.25,'sawtooth',0.18,180); },
    revenge(){ tone(330,0,0.11,'triangle',0.4,330); tone(660,0.1,0.22,'triangle',0.4,660); noise(0,0.12,0.18,3200); },
    enemyDie(){ tone(400,0,0.4,'sawtooth',0.3,50); noise(0,0.3,0.26,1200); },
    hurt(){ tone(130,0,0.3,'sine',0.45,40); noise(0,0.2,0.28,900); },
    correct(){ tone(660,0,0.1,'triangle',0.3); tone(880,0.08,0.14,'triangle',0.3); },
    wrong(){ tone(220,0,0.25,'sawtooth',0.28,110); },
    tap(){ tone(760,0,0.05,'sine',0.2); },
    key(){ tone(520,0,0.04,'sine',0.15); },
    levelup(){ [523,659,784,1047].forEach((f,i)=>tone(f,i*0.09,0.32,'triangle',0.35)); },
    reward(){ tone(1200,0,0.08,'square',0.16); tone(1600,0.07,0.12,'square',0.16); },
    flow(){ [523,659,784,1047,1319].forEach((f,i)=>tone(f,i*0.06,0.26,'sine',0.26)); },
    node(){ tone(880,0,0.4,'sine',0.26,880); tone(1320,0.05,0.5,'sine',0.18,1320); },
    victory(){
      [523,659,784,1047].forEach((f,i)=>tone(f,i*0.15,0.5,'sawtooth',0.26));
      [523,587,659,784,880,1047].forEach((f,i)=>tone(f,0.7+i*0.1,0.2,'triangle',0.28));
    },
    defeat(){ tone(330,0,0.6,'sawtooth',0.3,110); tone(220,0.3,0.8,'sine',0.28,55); }
  };
  function play(name){
    if(!AUD.sfx) return;
    const f=SFX[name]; if(f) f();
  }
  function fx(name,vib){ play(name); if(vib) vibrate(vib); }

  /* ── 震动（触感） ── */
  function vibrate(p){
    if(!AUD.vibrate) return;
    try{ navigator.vibrate(p); }catch(e){}
  }

  /* ── BGM 音序器（16 步 × 三种场景） ── */
  const BGM={
    home:{bpm:72,root:110,bassLen:7,
      bass:[0,null,null,null,null,null,0,null,null,null,null,null,null,null,null,null],
      lead:[null,null,12,null,null,null,null,null,7,null,null,10,null,null,null,null],
      kick:[],hat:[]},
    battle:{bpm:104,root:110,bassLen:2,
      bass:[0,null,null,0,null,null,0,null,5,null,null,0,null,null,7,null],
      lead:[null,null,null,null,12,null,null,null,null,null,10,null,null,null,null,null],
      kick:[0,4,8,12],hat:[2,6,10,14]},
    boss:{bpm:128,root:110,bassLen:2,
      bass:[0,0,null,0,0,null,0,0,3,0,null,0,0,0,null,0],
      lead:[12,null,12,null,10,null,12,null,15,null,12,null,10,null,7,null],
      kick:[0,3,6,8,11,14],hat:[2,4,6,10,12,14]}
  };
  function bgmTone(freq,t,dur,type,vol){
    const o=ctx.createOscillator(),g=ctx.createGain();
    o.type=type; o.frequency.value=freq;
    g.gain.setValueAtTime(0.0001,t); g.gain.linearRampToValueAtTime(vol,t+0.02);
    g.gain.exponentialRampToValueAtTime(0.001,t+dur);
    o.connect(g); g.connect(bgmBus); o.start(t); o.stop(t+dur+0.05);
  }
  function bgmKick(t){
    const o=ctx.createOscillator(),g=ctx.createGain();
    o.type='sine'; o.frequency.setValueAtTime(120,t);
    o.frequency.exponentialRampToValueAtTime(40,t+0.12);
    g.gain.setValueAtTime(0.45,t); g.gain.exponentialRampToValueAtTime(0.001,t+0.16);
    o.connect(g); g.connect(bgmBus); o.start(t); o.stop(t+0.2);
  }
  function bgmHat(t){
    const n=Math.floor(ctx.sampleRate*0.05);
    const buf=ctx.createBuffer(1,n,ctx.sampleRate);
    const d=buf.getChannelData(0);
    for(let i=0;i<n;i++) d[i]=Math.random()*2-1;
    const s=ctx.createBufferSource(); s.buffer=buf;
    const f=ctx.createBiquadFilter(); f.type='highpass'; f.frequency.value=7000;
    const g=ctx.createGain(); g.gain.setValueAtTime(0.1,t);
    g.gain.exponentialRampToValueAtTime(0.001,t+0.05);
    s.connect(f); f.connect(g); g.connect(bgmBus); s.start(t);
  }
  function scheduleStep(step,t,cfg,spb){
    const b=cfg.bass[step];
    if(b!==null&&b!==undefined) bgmTone(cfg.root*Math.pow(2,b/12),t,spb*cfg.bassLen,'triangle',0.14);
    const l=cfg.lead[step];
    if(l!==null&&l!==undefined) bgmTone(cfg.root*4*Math.pow(2,l/12),t,spb*1.7,'sine',0.08);
    if(cfg.kick.includes(step)) bgmKick(t);
    if(cfg.hat.includes(step)) bgmHat(t);
  }
  function scheduler(){
    const cfg=BGM[bgmMode];
    const spb=60/cfg.bpm/4;
    while(nextStepTime<ctx.currentTime+0.12){
      scheduleStep(bgmStep,nextStepTime,cfg,spb);
      nextStepTime+=spb; bgmStep=(bgmStep+1)%16;
    }
  }
  function startBgm(mode){
    if(!ensure()) return;
    bgmMode=mode||'home';
    if(!AUD.bgm) return;          // BGM 被关闭：不启动调度
    if(schedTimer) return;
    bgmStep=0; nextStepTime=ctx.currentTime+0.1;
    schedTimer=setInterval(scheduler,25);
  }
  function switchBgm(mode){
    bgmMode=mode;
    if(!schedTimer) startBgm(mode);
  }
  function stopBgm(){ if(schedTimer){clearInterval(schedTimer);schedTimer=null;} }

  return {unlock,applyVol,play,fx,vibrate,startBgm,switchBgm,stopBgm,ensure};
})();

/* 首次任意点击/触摸：解锁音频（浏览器自动播放策略），并按场景启动 BGM */
function unlockAudioOnce(){
  AudioEngine.unlock();
  const s=G?G.screen:'home';
  AudioEngine.startBgm(s==='battle'?(G.battle&&G.battle.kind==='boss'?'boss':'battle'):'home');
}
document.addEventListener('pointerdown',()=>{ unlockAudioOnce(); },{once:true});
