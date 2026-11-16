// Angular redesign mockups: the same hero in the same 300x280 space, built only from straight-edged polygons.
const STYLES = {
  facet: { name: 'Faceted', facet: .26, ink: 1.4, inkA: .7, spikes: false, hatch: false, edge: false },
  shard: { name: 'Shard', facet: 0, ink: 2.6, inkA: .95, spikes: true, hatch: false, edge: false },
  carved: { name: 'Carved', facet: .2, ink: 2, inkA: .9, spikes: false, hatch: true, edge: true },
};
const AFACES = {
  square: 'M135 70L143 62H159L167 70L168 90L160 104L151 109L142 104L134 90Z',
  diamond: 'M136 72L144 62H158L166 72L168 86L151 110L134 86Z',
  long: 'M136 68L143 60H159L166 68L166 94L158 108L151 112L144 108L136 94Z',
};
const AHAIR = {
  windswept: ['', 'M131 86L129 66L138 55L146 51L150 40L157 50L170 43L180 38L173 54L188 60L173 66L171 86L166 72L158 70L161 79L150 70L141 74L144 64L136 75Z'],
  crop: ['', 'M132 84L130 64L140 55H162L172 64L170 84L166 74L162 80L158 70L153 78L148 69L142 79L137 71Z'],
  shaggy: ['', 'M129 98L127 64L138 53H164L175 64L173 98L168 88L168 76L163 83L159 71L154 81L149 70L144 81L139 72L136 83L133 77Z'],
  long: ['M130 78L136 57L151 51L166 57L172 78L178 134L162 128L156 112H146L140 128L124 134Z', 'M131 86L133 63L142 55H160L169 63L171 86L165 70L151 64L137 70Z'],
  ponytail: ['M163 61L192 70L188 104L176 132L172 98L166 82Z', 'M131 84L133 62L142 54H160L169 62L171 84L165 70L151 66L137 70Z'],
  braid: ['', 'M131 84L133 62L142 54H160L169 62L171 84L165 70L151 66L137 70Z'],
  topknot: ['M144 54L146 42L151 37L156 42L158 54Z', 'M132 82L134 62L143 56H159L168 62L170 82L165 68L151 64L137 68Z'],
  shorn: ['', 'M134 78L136 64L143 60H159L166 64L168 78L161 68H141Z'],
};
const AHAIRC = { raven:'#1d1714', chestnut:'#3e2a1e', oak:'#6b4a2e', ember:'#8a3c24', wheat:'#d6b46a', ash:'#9a9ea2', frost:'#ece6da', dusk:'#3b4566' };
const ASKIN = { porcelain:'#f3d7c0', fair:'#e8c09c', sand:'#d6a57c', olive:'#b98260', bronze:'#9a6646', umber:'#7a4e34', deep:'#5a3826', ebony:'#3e2619' };
const ACLOAK = {
  forest:{a:'#52817a',b:'#163b3e',hood:'#547f73',arms:'#6e8c7b',torso:'#35565a',shade:'#152e32'},
  crimson:{a:'#9a4a3e',b:'#3a1513',hood:'#8a4a40',arms:'#8a6a60',torso:'#4a2a28',shade:'#2a1210'},
  midnight:{a:'#3f5a8a',b:'#121b33',hood:'#3c5580',arms:'#6a7d9a',torso:'#26324d',shade:'#0e1626'},
  ash:{a:'#6a7072',b:'#1e2224',hood:'#5c6466',arms:'#7d8587',torso:'#33393b',shade:'#15191a'},
  ochre:{a:'#a07a3c',b:'#3a2a12',hood:'#8e6c38',arms:'#8a7a5a',torso:'#4a3a22',shade:'#241a0c'},
  plum:{a:'#6b5b95',b:'#2b2140',hood:'#7c6aa6',arms:'#8e7fb5',torso:'#3b3157',shade:'#1d1730'},
};
const K = '#140d0a';

function ahero(o = {}) {
  const p = Object.assign({ style:'facet', sex:'m', skin:'sand', face:'square', hair:'windswept', hairColor:'chestnut', ears:'pointed', eyes:'shard', cloak:'forest', hood:'down', weapon:'sword', portrait:true, w:200, crop:null }, o);
  const S = STYLES[p.style], L = ACLOAK[p.cloak], skin = ASKIN[p.skin], hc = AHAIRC[p.hairColor], f = p.sex === 'f';
  const ink = (w = 1) => `stroke="${K}" stroke-width="${S.ink * w}" stroke-opacity="${S.inkA}" stroke-linejoin="miter"`;
  const dark = (d) => S.facet ? `<path d="${d}" fill="#000" opacity="${S.facet}"/>` : '';
  const hatch = (d, id) => S.hatch ? `<clipPath id="${id}"><path d="${d}"/></clipPath><g clip-path="url(#${id})" stroke="${K}" stroke-opacity=".35" stroke-width="1">${Array.from({length:30},(_,i)=>`<path d="M${60+i*7} 280L${160+i*7} 20"/>`).join('')}</g>` : '';
  const edge = (d, c = '#f3e2b6') => S.edge ? `<path d="${d}" stroke="${c}" stroke-width="1.2" fill="none" opacity=".55"/>` : '';
  const uid = Math.random().toString(36).slice(2, 7);
  let s = '';
  if (p.portrait) {
    s += `<radialGradient id="h${uid}"><stop stop-color="#d2b266" stop-opacity=".22"/><stop offset="1" stop-color="#d2b266" stop-opacity="0"/></radialGradient><circle cx="150" cy="140" r="125" fill="url(#h${uid})"/>`;
    s += `<path d="M150 35L221 64L250 135L221 206L150 235L79 206L50 135L79 64Z" fill="none" stroke="#bfa164" stroke-opacity=".3" stroke-width="1.5"/><path d="M150 18V30M150 240V252M32 135H44M256 135H268" stroke="#bfa164" stroke-width="1.5"/>`;
  }
  s += `<path d="M82 250L110 241H190L218 250L190 259H110Z" fill="#071113"/>`;
  if (p.portrait) s += `<path d="M58 250L100 238H200L242 250L200 262H100Z" fill="none" stroke="#bfa164" stroke-opacity=".5"/>`;
  // Cape with a shard-cut hem.
  const hem = S.spikes ? 'L212 236L196 228L188 248L174 230L162 250L150 232L138 250L126 230L112 248L104 228L88 236' : 'L212 232L198 226L186 238L174 228L162 242L150 230L138 242L126 228L114 238L102 226L90 232';
  const cape = `M120 118L181 118${hem}Z`;
  s += `<path d="${cape}" fill="${L.a}"/>` + dark(`M151 118L181 118${hem.split('L150')[0]}L150 ${S.spikes ? 232 : 230}Z`) + hatch(`M151 118L181 118L212 236L150 236Z`, 'c' + uid) + `<path d="${cape}" fill="none" ${ink()}/><path d="M131 150L114 226M171 150L188 226M151 150V230" stroke="${K}" stroke-opacity=".3" stroke-width="1.2"/>`;
  // Back items.
  if (p.weapon === 'bow') s += `<path d="M104 92L122 70L138 84L120 106Z" fill="#5a3a26" ${ink(.6)}/><path d="M108 78L100 64M114 72L108 56M120 66L116 52" stroke="#e8e2d0" stroke-width="1.2"/><path d="M100 64l-5-5 2 7zM108 56l-4-6v7zM116 52l-3-6-2 7z" fill="#c9a86a"/>`;
  // Legs and pointed boots.
  const legs = f ? 'M126 154L120 234H142L151 188L158 234H180L174 154Z' : 'M124 154L118 234H141L151 186L160 234H182L176 154Z';
  s += `<path d="${legs}" fill="#14272d" ${ink(.6)}/>` + dark('M151 154H176L182 234H160L151 186Z');
  s += `<path d="M118 228H143V248H102Z" fill="#9a8061" ${ink(.6)}/><path d="M159 228H184L200 248H159Z" fill="#9a8061" ${ink(.6)}/>` + dark('M130 238H143V248H112Z') + dark('M172 238H184L200 248H172Z');
  // Arms and torso.
  const arms = f ? ['M120 118L106 125L104 166L123 170L129 140Z', 'M180 118L194 125L196 164L177 168L171 140Z'] : ['M118 118L103 124L101 166L122 170L128 140Z', 'M182 118L197 124L199 164L178 168L172 140Z'];
  arms.forEach((a, i) => { s += `<path d="${a}" fill="${L.arms}" ${ink(.8)}/>` + (i ? dark(a) : ''); });
  const torso = f ? 'M128 112H174L168 146L180 172L151 180L122 172L134 146Z' : 'M126 112H176L184 172L151 180L118 172Z';
  s += `<path d="${torso}" fill="${L.torso}" ${ink()}/>` + dark(f ? 'M151 112H174L168 146L180 172L151 180Z' : 'M151 112H176L184 172L151 180Z') + hatch(f ? 'M151 112H174L168 146L180 172L151 180Z' : 'M151 112H176L184 172L151 180Z', 't' + uid);
  // Cuffs.
  s += `<path d="M102 158L122 162L122 170L101 166Z" fill="#b49b6d"/><path d="M198 156L178 160L178 168L199 164Z" fill="#b49b6d"/>`;
  if (S.spikes) s += `<path d="M108 112L136 108L130 126L96 132Z" fill="${L.a}" ${ink()}/><path d="M192 112L164 108L170 126L204 132Z" fill="${L.a}" ${ink()}/><path d="M96 132L102 118M204 132L198 118" stroke="#e6c277" stroke-width="2"/>`;
  // Chevron belt and diamond buckle.
  s += `<path d="M121 146L151 154L181 146V155L151 163L121 155Z" fill="#b49b6d" ${ink(.5)}/><path d="M151 149L158 158L151 167L144 158Z" fill="#eac774" ${ink(.5)}/>`;
  // Head.
  if (p.hood === 'up') {
    const hood = S.spikes ? 'M116 110L126 64L151 30L176 64L186 110L168 126H134Z' : 'M118 108L128 66L151 40L174 66L184 108L168 124H134Z';
    s += `<path d="${hood}" fill="${L.hood}" ${ink()}/>` + dark(S.spikes ? 'M151 30L176 64L186 110L168 126H151Z' : 'M151 40L174 66L184 108L168 124H151Z') + hatch('M151 40L184 108L151 124Z', 'h' + uid);
    s += `<path d="M131 92L151 72L171 92L163 114H139Z" fill="${L.shade}"/>`;
    s += `<path d="M139 96L147 94.5M155 94.5L163 96" stroke="#edce83" stroke-width="3" stroke-linecap="square"/>`;
    s += `<path d="M151 ${S.spikes ? 36 : 46}L157 56L151 66L145 56Z" fill="#e6c277"/>` + edge('M118 108L128 66L151 40');
  } else {
    const [back, front] = AHAIR[p.hair];
    if (back) s += `<path d="${back}" fill="${hc}" ${ink(.7)}/>` + dark(back.includes('L192') ? '' : '');
    const cowl = 'M116 124L124 106L140 104L151 114L162 104L178 106L186 124L170 117L151 126L132 117Z';
    s += `<path d="${cowl}" fill="${L.hood}" ${ink(.8)}/>` + dark('M151 114L162 104L178 106L186 124L170 117L151 126Z');
    s += `<path d="M145 99H157L159 116H143Z" fill="${skin}"/><path d="M144 106L151 110L158 106V116H144Z" fill="#000" opacity=".2"/>`;
    const earL = p.ears === 'pointed' ? (S.spikes ? 'M136 80L114 64L135 98Z' : 'M136 80L119 68L135 96Z') : 'M136 82L130 84L130 95L136 97Z';
    const earR = p.ears === 'pointed' ? (S.spikes ? 'M166 80L188 64L167 98Z' : 'M166 80L183 68L167 96Z') : 'M166 82L172 84L172 95L166 97Z';
    s += `<path d="${earL}" fill="${skin}" ${ink(.7)}/><path d="${earR}" fill="${skin}" ${ink(.7)}/>` + dark(earR);
    let face = AFACES[p.face];
    s += `<g ${f ? 'transform="translate(151 0) scale(.93 1) translate(-151 0)"' : ''}><path d="${face}" fill="${skin}" ${ink()}/>`;
    // Shadow plane on the far cheek.
    if (S.facet) s += `<clipPath id="f${uid}"><path d="${face}"/></clipPath><path clip-path="url(#f${uid})" d="M156 60L172 60L172 112L151 112L158 92Z" fill="#000" opacity="${S.facet * .8}"/>`;
    s += `</g>`;
    s += aeyes(p.eyes, f, hc);
    s += `<path d="M151 89L148.5 96H152.5" stroke="${K}" stroke-width="1.1" fill="none" opacity=".5"/>`;
    s += p.eyes === 'bright' ? `<path d="M146.5 100.5L151 103.5L155.5 100.5" stroke="${K}" stroke-width="1.4" fill="none" opacity=".75"/>` : `<path d="M147.5 101.5L151 102.6L154.5 101.5" stroke="${K}" stroke-width="1.3" fill="none" opacity=".6"/>`;
    s += `<path d="${front}" fill="${hc}" ${ink(.8)}/>`;
    if (p.hair === 'braid') for (let i = 0; i < 6; i++) { const y = 94 + i * 8, x = 168 + (i % 2 ? 1.5 : -1.5); s += `<path d="M${x} ${y-5}L${x+5} ${y}L${x} ${y+5}L${x-5} ${y}Z" fill="${hc}" ${ink(.6)}/>`; }
    if (p.hair === 'ponytail') s += `<path d="M163 60L170 63L168 70L161 67Z" fill="#c9a86a"/>`;
    s += edge('M136 70L143 62H151');
  }
  // Brooch.
  s += `<path d="M151 110L158 118L151 128L144 118Z" fill="#8fb58a" stroke="#e6c277" stroke-width="1.2"/>`;
  s += aweapon(p.weapon);
  // Hex fists.
  const fist = (x, y) => `<path d="M${x-8} ${y-6}L${x-4} ${y-10}H${x+4}L${x+8} ${y-6}V${y+6}L${x+4} ${y+9}H${x-4}L${x-8} ${y+6}Z" fill="${skin}" ${ink(.6)}/><path d="M${x-7} ${y-2}H${x-1}M${x-7} ${y+3}H${x-1}" stroke="${K}" stroke-width=".9" opacity=".45"/>`;
  s += fist(112, 176) + fist(190, 173);
  if (p.portrait) s += `<path d="M62 94l3 9 6-9-6-0z" fill="#e9cb83"/><path d="M60 100L66 92L72 100L66 108Z" fill="#e9cb83"/><path d="M228 196L232 191L236 196L232 201Z" fill="#e9cb83"/>`;
  const vb = p.crop || '0 0 300 280';
  const [, , vw, vh] = vb.split(' ').map(Number);
  return `<svg viewBox="${vb}" width="${p.w}" height="${Math.round(p.w * vh / vw)}">${s}</svg>`;
}

function aeyes(st, f, hc) {
  let s = '';
  const brow = st === 'shard' ? 'M139 82.5L148 84M154 84L163 82.5' : 'M139.5 81.5L147.5 80.5M154.5 80.5L162.5 81.5';
  s += `<path d="${brow}" stroke="${hc}" stroke-width="${f ? 1.6 : 2.4}" stroke-linecap="square"/>`;
  if (st === 'shard') {
    // Slanted shard eyes: a white sliver, a dark iris wedge and a square glint.
    s += `<path d="M140 87L148.5 87.5L147 90.5L141.5 90Z" fill="#f4ede0"/><path d="M162 87L153.5 87.5L155 90.5L160.5 90Z" fill="#f4ede0"/>`;
    s += `<path d="M143 87.3L147 87.5L146 90.3L143.6 90.1Z" fill="${K}"/><path d="M159 87.3L155 87.5L156 90.3L158.4 90.1Z" fill="${K}"/>`;
    s += `<path d="M139.5 86.8L148.8 87.4M162.5 86.8L153.2 87.4" stroke="${K}" stroke-width="1.8"/>`;
  } else if (st === 'bright') {
    s += `<path d="M141 85L144.5 83.5L148 85L147.5 91.5L144.5 93L141.5 91.5Z" fill="#f8f4ec"/><path d="M161 85L157.5 83.5L154 85L154.5 91.5L157.5 93L160.5 91.5Z" fill="#f8f4ec"/>`;
    s += `<path d="M142.5 86.5L145 85.5L147 86.5L146.5 91L144.6 92L143 91Z" fill="#56688f"/><path d="M159.5 86.5L157 85.5L155 86.5L155.5 91L157.4 92L159 91Z" fill="#56688f"/>`;
    s += `<path d="M144 87.5H146V91H144ZM156 87.5H158V91H156Z" fill="#1f2638"/><path d="M145.2 86.6H146.6V88H145.2ZM157.2 86.6H158.6V88H157.2Z" fill="#fff"/>`;
    s += `<path d="M140.5 85.2L144.5 83L148.5 85.2M161.5 85.2L157.5 83L153.5 85.2" stroke="${K}" stroke-width="1.9" fill="none"/>`;
  } else if (st === 'slit') {
    s += `<path d="M140.5 88.5L148 87.6M154 87.6L161.5 88.5" stroke="${K}" stroke-width="2.6"/>`;
  }
  if (f) s += `<path d="M140 86.8L136.6 84.4L139.4 88Z" fill="${K}"/><path d="M162 86.8L165.4 84.4L162.6 88Z" fill="${K}"/>`;
  return s;
}

function aweapon(t) {
  const BH = '#dcf5e3', BL = '#96c8ba', G = '#c9a86a', W = '#8c6951';
  switch (t) {
    case 'sword': return `<path d="M185 74L190 58L195 74L194 160H186Z" fill="${BH}" stroke="${K}" stroke-opacity=".5"/><path d="M185 74L190 58V160H186Z" fill="${BL}"/><path d="M174 158H206L200 166H180Z" fill="${G}"/><path d="M187 164H193V186H187Z" fill="${W}"/><path d="M190 184L195 190L190 196L185 190Z" fill="${G}"/>`;
    case 'staff': return `<path d="M190 66V248" stroke="${W}" stroke-width="7"/><path d="M180 66L190 74L200 66" stroke="${G}" stroke-width="3" fill="none"/><circle cx="190" cy="48" r="22" fill="#9cd2bd" opacity=".25"/><path d="M190 30L201 48L190 66L179 48Z" fill="#bfeedd" stroke="${K}" stroke-opacity=".5"/><path d="M190 30L201 48L190 48Z" fill="#fff" opacity=".55"/><path d="M190 48L201 48L190 66Z" fill="#000" opacity=".15"/>`;
    case 'bow': return `<path d="M178 104L196 128L203 173L196 218L178 242" stroke="${W}" stroke-width="5" fill="none" stroke-linejoin="miter"/><path d="M178 104V242" stroke="#e8e2d0" stroke-width="1.2"/>`;
    case 'axe': return `<g transform="rotate(16 190 173)"><path d="M190 64V230" stroke="#5a3326" stroke-width="7"/><path d="M190 158V230" stroke="#1e1a1c" stroke-width="7"/><path d="M184 68L156 52L148 81L156 110L184 94Z" fill="${BH}" stroke="${K}" stroke-opacity=".6"/><path d="M196 68L224 52L232 81L224 110L196 94Z" fill="${BH}" stroke="${K}" stroke-opacity=".6"/><path d="M156 52L148 81L156 110M224 52L232 81L224 110" stroke="${BL}" stroke-width="2.5" fill="none"/><path d="M183 60H197V104H183Z" fill="#e3b54a" stroke="${K}" stroke-opacity=".5"/><path d="M185 52H195V60H185ZM183 104H197L193 112H187Z" fill="#e3b54a"/></g>`;
    case 'scythe': return `<path d="M198 40L186 246" stroke="${W}" stroke-width="6"/><path d="M198 42L238 38L266 74L234 54L200 58Z" fill="${BH}" stroke="${K}" stroke-opacity=".5"/>`;
  }
  return '';
}
