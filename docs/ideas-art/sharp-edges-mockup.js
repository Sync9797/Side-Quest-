// "Sharp curves" redesign: curved shapes that always meet in sharp points, hard-edged cel shading and thick ink.
const CK = '#1a1210';
const CSKIN = { porcelain:['#f6dcc6','#d9a98c'], fair:['#ecc4a0','#c99470'], sand:['#d9a77c','#ae7a55'], olive:['#bc8460','#8f5c40'], bronze:['#9c6846','#6f4630'], umber:['#7c5036','#553422'], deep:['#5c3a28','#3d2418'], ebony:['#40281a','#2a180e'] };
const CHAIR = { raven:['#2a2220','#120c0a','#5a4c48'], chestnut:['#4a3222','#2a1a10','#7a5a40'], oak:['#7a5634','#4c321c','#a8825a'], ember:['#a4472a','#6a2616','#d8744a'], wheat:['#dcb86c','#a8843e','#f6e0a0'], ash:['#a2a6aa','#6c7074','#d6dadc'], frost:['#eee8dc','#bcb4a6','#ffffff'], dusk:['#45507a','#262c4c','#7a86b4'] };
const CCLOAK = {
  forest:['#5a8a80','#2c5550','#8ab8ac'], crimson:['#a8503f','#5e231b','#d47a62'], midnight:['#46629a','#1f2d52','#7a94c8'],
  ash:['#727a7c','#3a4042','#a0a8aa'], ochre:['#b08640','#634618','#dcb46a'], plum:['#7462a2','#3a2c5c','#a290d0'],
};
const TUNIC = { forest:['#3b5e62','#1c3436'], crimson:['#56302c','#2c1412'], midnight:['#2c3a5a','#141c30'], ash:['#3a4244','#1a1e20'], ochre:['#544228','#2a2010'], plum:['#443862','#221a36'] };

function chero(o = {}) {
  const p = Object.assign({ sex:'m', skin:'sand', hair:'windswept', hairColor:'chestnut', ears:'pointed', eyes:'bright', cloak:'forest', hood:'down', weapon:'sword', portrait:true, w:200, chibi:1, crop:null, ink:2.2 }, o);
  const f = p.sex === 'f', [sk, skS] = CSKIN[p.skin], [hc, hcS, hcH] = CHAIR[p.hairColor], [ca, cS, cH] = CCLOAK[p.cloak], [tu, tuS] = TUNIC[p.cloak];
  const ink = (w = 1) => `stroke="${CK}" stroke-width="${p.ink * w}" stroke-linejoin="miter" stroke-miterlimit="8"`;
  const P = (d, fill, w = 1) => `<path d="${d}" fill="${fill}" ${w ? ink(w) : ''}/>`;
  const F = (d, fill, a = 1) => `<path d="${d}" fill="${fill}"${a < 1 ? ` opacity="${a}"` : ''}/>`;
  let s = '';
  if (p.portrait) {
    const id = 'g' + Math.random().toString(36).slice(2, 7);
    s += `<radialGradient id="${id}"><stop stop-color="#d2b266" stop-opacity=".22"/><stop offset="1" stop-color="#d2b266" stop-opacity="0"/></radialGradient><circle cx="150" cy="140" r="125" fill="url(#${id})"/>`;
    s += `<circle cx="150" cy="135" r="100" fill="none" stroke="#bfa164" stroke-opacity=".28" stroke-width="1.5"/><path d="M150 14L153 30L150 34L147 30ZM150 236L153 240L150 256L147 240ZM28 135L44 132L48 135L44 138ZM272 135L256 138L252 135L256 132Z" fill="#bfa164"/>`;
  }
  s += `<ellipse cx="150" cy="250" rx="66" ry="9" fill="#061012"/>`;
  if (p.portrait) s += `<ellipse cx="150" cy="250" rx="90" ry="12" fill="none" stroke="#bfa164" stroke-opacity=".45"/>`;
  // Cloak: curved sides, a tattered hem of sharp points; a hard-edged shadow down the far side.
  const cape = 'M121 118Q104 170 90 232Q100 228 106 240Q116 230 124 246Q134 232 142 248Q150 236 158 248Q166 232 176 246Q184 230 194 240Q200 228 212 232Q198 170 180 118Z';
  s += P(cape, ca);
  s += F('M164 118L180 118Q198 170 212 232Q200 228 194 240Q184 230 176 246Q172 236 170 230L176 200L166 176L174 150Z', cS);
  s += F('M126 128Q114 170 104 222L110 220Q118 176 132 132Z', cH, .7);
  s += P(cape, 'none');
  if (p.weapon === 'bow') s += P('M104 92L122 70L138 84L120 106Z', '#5a3a26', .6) + `<path d="M108 78L100 64M114 72L108 56M120 66L116 52" stroke="#e8e2d0" stroke-width="1.4"/>` + F('M100 64l-5-5 2 7zM108 56l-4-6v7zM116 52l-3-6-2 7z', '#c9a86a');
  // Legs, boots with pointed toes.
  const legs = f ? 'M126 156L121 232H142L151 190L158 232H180L175 156Z' : 'M124 156L119 232H141L151 188L160 232H182L177 156Z';
  s += P(legs, '#18292e', .7) + F('M156 156H177L182 232H164L160 200Z', '#0c1618');
  s += P('M119 226H143Q144 240 142 248H100Q104 236 119 232Z', '#9a7a58', .7) + P('M159 226H183Q186 234 202 248H159Q158 238 159 226Z', '#9a7a58', .7);
  s += F('M130 236H143L142 248H118Z', '#6a5038') + F('M172 234H183L202 248H176Z', '#6a5038');
  // Arms: curved sleeves cut to a point at the cuff.
  const armL = f ? 'M122 118Q106 120 104 136L104 166L124 170L128 142Z' : 'M120 118Q103 120 101 136L101 166L123 170L128 142Z';
  const armR = f ? 'M178 118Q194 120 196 136L196 164L176 168L172 140Z' : 'M180 118Q197 120 199 136L199 164L177 168L172 140Z';
  s += P(armL, ca, .9) + P(armR, ca, .9) + F('M186 122Q197 126 199 140L199 164L188 166L190 140Z', cS);
  s += P('M101 156L124 162L123 172L100 166Z', '#c4a36a', .6) + P('M199 154L176 160L177 170L200 164Z', '#c4a36a', .6);
  // Tunic: curved sides, pointed hem, a jagged cel shadow.
  const torso = f ? 'M128 112H174Q170 128 168 146Q176 158 180 172L151 182L122 172Q126 158 134 146Q132 128 128 112Z' : 'M126 112H176Q180 140 184 172L151 182L118 172Q122 140 126 112Z';
  s += P(torso, tu) + F(f ? 'M160 112H174Q170 128 168 146Q176 158 180 172L151 182L158 164L152 150L162 134L156 122Z' : 'M160 112H176Q180 140 184 172L151 182L158 164L152 150L162 134L156 122Z', tuS);
  s += P('M121 146Q151 158 181 146L181 155Q151 167 121 155Z', '#c4a36a', .6) + P('M151 150L158 159L151 169L144 159Z', '#f0cc74', .6);
  // Head (scaled up for the chibi look, pivoting on the neck).
  let h = '';
  if (p.hood === 'up') {
    const hood = 'M116 110Q118 70 151 38Q184 70 186 110L168 126H134Z';
    h += P(hood, ca) + F('M151 38Q184 70 186 110L168 126H160L172 96L160 70Z', cS) + F('M126 98Q128 70 151 46L132 76Z', cH, .6);
    h += F('M131 94Q151 70 171 94L163 116H139Z', '#0a0f12');
    h += `<path d="M139 97L147 95.5M155 95.5L163 97" stroke="#f2d488" stroke-width="3"/>`;
    h += P('M151 44L157 55L151 66L145 55Z', '#e6c277', .5);
  } else {
    h += hairBack(p.hair, hc, hcS, P, F);
    h += P('M116 124Q120 106 140 104L151 114L162 104Q182 106 186 124Q170 116 151 126Q132 116 116 124Z', ca, .9) + F('M162 104Q182 106 186 124Q172 118 160 122L170 112Z', cS);
    h += F('M144 99H158L160 117H142Z', sk) + F('M143 104L151 112L159 104V117H143Z', skS);
    const eL = p.ears === 'pointed' ? 'M136 82Q126 74 116 66Q122 84 135 98Z' : 'M136 82Q128 82 128 89Q128 96 136 97Z';
    const eR = p.ears === 'pointed' ? 'M166 82Q176 74 186 66Q180 84 167 98Z' : 'M166 82Q174 82 174 89Q174 96 166 97Z';
    h += P(eL, sk, .8) + P(eR, sk, .8) + F(p.ears === 'pointed' ? 'M168 84Q178 76 184 70Q178 86 168 94Z' : 'M167 86Q172 87 171 93L167 94Z', skS);
    // Face: rounded cheeks into a pointed chin.
    const face = f ? 'M137 72Q139 61 151 60Q163 61 165 72L166 88Q164 99 151 109Q138 99 136 88Z' : 'M135 72Q138 61 151 60Q164 61 167 72L168 88Q166 100 151 110Q136 100 134 88Z';
    h += P(face, sk);
    // A hard cel shadow under the hair line and down the far cheek.
    h += F(f ? 'M160 76L166 76L166 88Q164 99 151 109L156 98L160 92L157 86Z' : 'M161 76L168 76L168 88Q166 100 151 110L157 98L161 92L158 86Z', skS);
    h += ceyes(p.eyes, f, hc);
    h += `<path d="M151 90L149 96L152.5 96.5" stroke="${CK}" stroke-width="1.1" fill="none" opacity=".55"/>`;
    h += `<path d="M146.5 101Q151 104.4 155.5 101" stroke="${CK}" stroke-width="1.4" fill="none" opacity=".75"/>`;
    h += hairFront(p.hair, hc, hcS, hcH, P, F);
  }
  if (p.chibi !== 1) s += `<g transform="translate(151 116) scale(${p.chibi}) translate(-151 -116)">${h}</g>`; else s += h;
  s += P('M151 112L158 119L151 129L144 119Z', '#8fb58a', .5) + `<path d="M151 113V128" stroke="#e6c277" stroke-width="1.1"/>`;
  s += cweapon(p.weapon, P, F);
  // Fists: rounded backs, squared knuckles.
  const fist = (x, y) => P(`M${x-8} ${y-4}Q${x-8} ${y-10} ${x-2} ${y-10}H${x+6}L${x+8} ${y-6}V${y+6}L${x+5} ${y+9}H${x-3}Q${x-8} ${y+9} ${x-8} ${y+4}Z`, sk, .7) + F(`M${x+2} ${y-10}H${x+6}L${x+8} ${y-6}V${y+6}L${x+5} ${y+9}H${x+2}Z`, skS) + `<path d="M${x-7} ${y-2}H${x-1}M${x-7} ${y+3}H${x-1}" stroke="${CK}" stroke-width=".9" opacity=".5"/>`;
  s += fist(112, 176) + fist(190, 173);
  if (p.portrait) s += F('M62 92L65 100L73 103L65 106L62 114L59 106L51 103L59 100Z', '#e9cb83') + F('M230 192L232 197L237 199L232 201L230 206L228 201L223 199L228 197Z', '#e9cb83');
  const vb = p.crop || '0 0 300 280';
  const [, , vw, vh] = vb.split(' ').map(Number);
  return `<svg viewBox="${vb}" width="${p.w}" height="${Math.round(p.w * vh / vw)}">${s}</svg>`;
}

function hairBack(st, c, cs, P, F) {
  switch (st) {
    case 'long': return P('M130 80Q128 56 151 52Q174 56 172 80Q176 108 180 136L166 128L162 140L154 114H148L140 140L136 128L122 136Q126 108 130 80Z', c) + F('M160 70Q172 80 172 96Q176 116 180 136L166 128L168 104Z', cs);
    case 'ponytail': return P('M162 60Q188 60 192 84Q192 112 176 136Q180 112 174 96Q170 82 164 74Z', c) + F('M180 76Q190 96 182 120L176 136Q180 110 176 92Z', cs);
    case 'topknot': return P('M144 56Q142 44 151 34Q160 44 158 56Z', c) + F('M151 34Q160 44 158 56H152Z', cs);
    default: return '';
  }
}

function hairFront(st, c, cs, ch, P, F) {
  const shine = (d) => F(d, ch, .8);
  switch (st) {
    case 'windswept': return P('M130 90Q124 64 142 54Q146 46 151 38Q155 46 160 48Q172 42 186 38Q178 50 177 56Q188 58 192 66Q181 66 173 68Q175 78 171 90Q166 76 158 71Q162 78 160 84Q152 72 141 75Q143 69 146 64Q136 72 130 90Z', c)
      + F('M171 90Q175 78 173 68Q181 66 192 66Q184 62 176 60Q168 64 166 74Z', cs) + shine('M140 60Q148 52 156 52L146 62Z');
    case 'crop': return P('M131 86Q127 60 151 54Q175 60 171 86L167 74L163 81L159 70L153 79L148 69L142 80L138 71Z', c) + F('M160 58Q171 64 171 86L167 74L163 81L161 70Z', cs) + shine('M140 62Q146 57 154 57L144 65Z');
    case 'shaggy': return P('M128 100Q122 60 151 52Q180 60 174 100L168 90L168 78L162 86L159 72L154 83L149 70L144 83L139 72L136 86L132 78Z', c) + F('M162 58Q178 66 174 100L168 90L168 78L163 84Z', cs) + shine('M138 62Q146 56 156 56L144 66Z');
    case 'long': return P('M131 88Q129 57 151 54Q173 57 171 88Q166 70 151 64Q136 70 131 88Z', c) + F('M158 58Q171 64 171 88Q168 74 160 68Z', cs) + shine('M138 66Q144 60 150 59L141 68Z');
    case 'ponytail': return P('M131 86Q129 56 151 54Q173 56 171 86Q166 68 151 66Q136 68 131 86Z', c) + F('M158 57Q171 63 171 86Q168 72 161 67Z', cs) + shine('M139 63Q145 58 151 58L142 66Z') + P('M162 59L170 62L167 70L159 67Z', '#c9a86a', .5);
    case 'braid': {
      let s = P('M131 86Q129 56 151 54Q173 56 171 86Q166 68 151 66Q136 68 131 86Z', c) + F('M158 57Q171 63 171 86Q168 72 161 67Z', cs);
      for (let i = 0; i < 6; i++) { const y = 94 + i * 8, x = 168 + (i % 2 ? 1.5 : -1.5); s += P(`M${x-5} ${y}Q${x-4} ${y-5} ${x} ${y-6}Q${x+4} ${y-5} ${x+5} ${y}L${x} ${y+5}Z`, c, .6); }
      return s + shine('M139 63Q145 58 151 58L142 66Z');
    }
    case 'topknot': return P('M132 84Q130 58 151 56Q172 58 170 84Q166 66 151 64Q136 66 132 84Z', c) + F('M158 59Q170 64 170 84Q167 70 161 66Z', cs) + P('M145 54H157L156 59H146Z', '#c9a86a', .5);
    case 'shorn': return F('M134 78Q134 61 151 60Q168 61 168 78Q160 68 151 68Q142 68 134 78Z', c, .9);
  }
  return '';
}

function ceyes(st, f, hc) {
  let s = `<path d="M139.5 81.5L147.8 80.2M154.2 80.2L162.5 81.5" stroke="${CHAIR[Object.keys(CHAIR).find(k => CHAIR[k][0] === hc)][1]}" stroke-width="${f ? 1.6 : 2.4}" stroke-linecap="butt"/>`;
  if (st === 'bright') {
    // Big eyes: a flat top lash line that kicks up to a point, a tall iris, a hard square glint.
    s += `<path d="M141 85.5L148 85.5L147.5 91Q144.5 93.5 141.5 91Z" fill="#fbf7ef"/><path d="M161 85.5L154 85.5L154.5 91Q157.5 93.5 160.5 91Z" fill="#fbf7ef"/>`;
    s += `<path d="M142.6 85.5H147L146.6 91Q144.8 92.6 143 91Z" fill="#4f6596"/><path d="M159.4 85.5H155L155.4 91Q157.2 92.6 159 91Z" fill="#4f6596"/>`;
    s += `<path d="M143.8 86.5H146V90.5H143.8ZM156 86.5H158.2V90.5H156Z" fill="#1c2236"/><path d="M144.6 86H146.4V87.8H144.6ZM156.6 86H158.4V87.8H156.6Z" fill="#fff"/>`;
    s += `<path d="M139.6 86.4L141.4 85L148.6 85.2M162.4 86.4L160.6 85L153.4 85.2" stroke="${CK}" stroke-width="2" fill="none"/>`;
    if (f) s += `<path d="M139.6 86.4L136.8 83.6L140.8 85.2ZM162.4 86.4L165.2 83.6L161.2 85.2Z" fill="${CK}"/>`;
  } else {
    // Narrow keen eyes.
    s += `<path d="M140.5 88.6L148 87.2L147 90Q144 91 141.5 90Z" fill="#fbf7ef"/><path d="M161.5 88.6L154 87.2L155 90Q158 91 160.5 90Z" fill="#fbf7ef"/>`;
    s += `<path d="M143.5 87.8H146.6V90.4H143.8ZM158.5 87.8H155.4V90.4H158.2Z" fill="${CK}"/>`;
    s += `<path d="M140 88.6L148.4 86.9M162 88.6L153.6 86.9" stroke="${CK}" stroke-width="2"/>`;
    if (f) s += `<path d="M140 88.6L137 86.2L141 88ZM162 88.6L165 86.2L161 88Z" fill="${CK}"/>`;
  }
  return s;
}

function cweapon(t, P, F) {
  const BH = '#e2f7ea', BL = '#9acdbf', G = '#d0ad6c', W = '#8c6951';
  switch (t) {
    case 'sword': return P('M185 76Q186 64 190 56Q194 64 195 76L194 160H186Z', BH, .7) + F('M190 58Q186 66 185.6 76L186 160H190Z', BL) + P('M174 158H206Q204 164 200 166H180Q176 164 174 158Z', G, .6) + F('M187 166H193V186H187Z', W) + P('M190 184L195 190L190 197L185 190Z', G, .5);
    case 'staff': return `<path d="M190 66V248" stroke="${W}" stroke-width="7"/>` + `<circle cx="190" cy="48" r="22" fill="#9cd2bd" opacity=".25"/>` + P('M190 28Q197 40 201 48Q197 56 190 68Q183 56 179 48Q183 40 190 28Z', '#c4f0e0', .6) + F('M190 28Q197 40 201 48L190 48Z', '#fff', .6) + P('M178 66Q190 76 202 66', 'none', 0) + `<path d="M178 66Q190 76 202 66" stroke="${G}" stroke-width="3" fill="none"/>`;
    case 'bow': return `<path d="M178 102Q192 116 198 140Q203 173 198 206Q192 230 178 244" stroke="${W}" stroke-width="5" fill="none"/><path d="M174 98L178 102L182 98M174 248L178 244L182 248" stroke="${G}" stroke-width="2" fill="none"/><path d="M178 102V244" stroke="#e8e2d0" stroke-width="1.2"/>`;
    case 'axe': return `<g transform="rotate(16 190 173)"><path d="M190 64V230" stroke="#5a3326" stroke-width="7"/><path d="M190 158V230" stroke="#1e1a1c" stroke-width="7"/>` + P('M184 70Q168 64 156 50Q146 80 156 112Q168 98 184 94Z', BH, .7) + P('M196 70Q212 64 224 50Q234 80 224 112Q212 98 196 94Z', BH, .7) + F('M156 50Q146 80 156 112L160 104Q152 80 160 58Z', BL) + F('M224 50Q234 80 224 112L220 104Q228 80 220 58Z', BL) + P('M183 60H197V104H183Z', '#e3b54a', .6) + F('M185 52H195V60H185ZM183 104H197L193 112H187Z', '#e3b54a') + `</g>`;
    case 'scythe': return `<path d="M198 40L186 246" stroke="${W}" stroke-width="6"/>` + P('M198 42Q238 32 266 76Q236 54 200 58Z', BH, .7) + F('M200 50Q234 44 262 70Q236 56 200 58Z', BL);
  }
  return '';
}
