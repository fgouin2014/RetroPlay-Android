// Sega Custom Controller for EmulatorJS
// Supports: Mega Drive (6-button), Sega CD, Saturn
(function() {

window.EJS_VirtualGamepadSettings = [
  // Shoulders (Saturn L/R)
  { type:"button", text:"L", id:"sega-l", location:"top", left:12, top:-40, fontSize:18, bold:true, block:true, input_value:10 },
  { type:"button", text:"R", id:"sega-r", location:"top", right:12, top:-40, fontSize:18, bold:true, block:true, input_value:11 },

  // Left D-Pad zone
  { type:"zone", location:"left", left:"52%", top:"42%", joystickInput:false, color:"#0066CC", inputValues:[4,5,6,7] },

  // Sega 6-button layout (right side) - Disposition authentique Sega
  // Rangée du haut: X, Y, Z (secondaires - plus petits)
  { type:"button", text:"X", id:"sega-x", location:"right", right:146, top:14,  fontSize:18, bold:true, input_value:8 },
  { type:"button", text:"Y", id:"sega-y", location:"right", right:86,  top:14,  fontSize:18, bold:true, input_value:9 },
  { type:"button", text:"Z", id:"sega-z", location:"right", right:26,  top:14,  fontSize:18, bold:true, input_value:12 },
  
  // Rangée du bas: A, B, C (principaux - plus gros, décalés vers le bas)
  { type:"button", text:"A", id:"sega-a", location:"right", right:146, top:74,  fontSize:26, bold:true, input_value:0 },
  { type:"button", text:"B", id:"sega-b", location:"right", right:86,  top:74,  fontSize:26, bold:true, input_value:1 },
  { type:"button", text:"C", id:"sega-c", location:"right", right:26,  top:74,  fontSize:26, bold:true, input_value:2 },

  // Start (CENTER)
  { type:"button", text:"START", id:"sega-start", location:"center", left:14, bottom:2, fontSize:14, bold:true, block:true, input_value:3 }
];

// Styling function
function styleSegaButtons() {
  const allElements = Array.from(document.querySelectorAll('*'));
  const segaLabels = ['A', 'B', 'C', 'X', 'Y', 'Z', 'L', 'R', 'START'];
  
  function isSegaLabel(el) {
    const text = (el && el.textContent || '').trim();
    return segaLabels.includes(text) ? text : null;
  }
  
  function getRootBox(el) {
    let n = el;
    for (let i = 0; i < 4 && n && n.parentElement; i++) {
      const s = n.style;
      if (s && (s.left || s.right || s.top || s.bottom)) break;
      n = n.parentElement;
    }
    return n || el;
  }
  
  function styleActionButton(btn, label) {
    // Boutons principaux (A, B, C) plus gros que secondaires (X, Y, Z)
    const isPrimary = ['A', 'B', 'C'].includes(label);
    const size = isPrimary ? '64px' : '52px';
    const fontSize = isPrimary ? '24px' : '20px';
    
    Object.assign(btn.style, {
      width: size,
      height: size,
      lineHeight: size,
      borderWidth: '2px',
      borderStyle: 'solid',
      borderRadius: '50%',
      textAlign: 'center',
      fontWeight: 'bold',
      fontSize: fontSize
    });
    
    // Sega colors: Blue theme
    const colors = {
      'A': ['#FF3333', 'rgba(255,51,51,0.15)'],    // Red (primary)
      'B': ['#FFD700', 'rgba(255,215,0,0.15)'],    // Gold (secondary)
      'C': ['#33CCFF', 'rgba(51,204,255,0.15)'],   // Cyan (tertiary)
      'X': ['#FF6600', 'rgba(255,102,0,0.15)'],    // Orange
      'Y': ['#66FF33', 'rgba(102,255,51,0.15)'],   // Green
      'Z': ['#9933FF', 'rgba(153,51,255,0.15)']    // Purple
    };
    
    const color = colors[label] || ['#0066CC', 'rgba(0,102,204,0.15)'];
    btn.style.color = color[0];
    btn.style.borderColor = color[0];
    btn.style.backgroundColor = color[1];
  }
  
  function styleShoulder(btn) {
    Object.assign(btn.style, {
      width: '70px',
      height: '34px',
      lineHeight: '34px',
      borderRadius: '8px',
      fontWeight: 'bold',
      fontSize: '18px',
      color: '#0066CC',
      borderColor: '#0066CC',
      backgroundColor: 'rgba(0,102,204,0.15)'
    });
  }
  
  function styleStart(btn) {
    Object.assign(btn.style, {
      width: '80px',
      height: '32px',
      lineHeight: '32px',
      borderRadius: '8px',
      fontWeight: 'bold',
      fontSize: '14px',
      color: '#0066CC',
      borderColor: '#0066CC',
      backgroundColor: 'rgba(0,102,204,0.15)'
    });
  }
  
  function applyStyles() {
    allElements.forEach(node => {
      const label = isSegaLabel(node);
      if (!label || node.__segaStyled) return;
      const box = getRootBox(node);
      
      if (['A', 'B', 'C', 'X', 'Y', 'Z'].includes(label)) {
        styleActionButton(box, label);
      } else if (['L', 'R'].includes(label)) {
        styleShoulder(box);
      } else if (label === 'START') {
        styleStart(box);
      }
      
      node.__segaStyled = true;
    });
  }
  
  // Add press effects for action buttons
  function addPressEffects() {
    const actionButtons = ['A', 'B', 'C', 'X', 'Y', 'Z'];
    const defaultColors = {
      'A': { fg:'#FF3333', bg:'rgba(255,51,51,0.15)' },
      'B': { fg:'#FFD700', bg:'rgba(255,215,0,0.15)' },
      'C': { fg:'#33CCFF', bg:'rgba(51,204,255,0.15)' },
      'X': { fg:'#FF6600', bg:'rgba(255,102,0,0.15)' },
      'Y': { fg:'#66FF33', bg:'rgba(102,255,51,0.15)' },
      'Z': { fg:'#9933FF', bg:'rgba(153,51,255,0.15)' }
    };
    const pressedColors = {
      'A': { fg:'#CC0000', bg:'rgba(0,0,0,0.22)' },
      'B': { fg:'#CCAA00', bg:'rgba(0,0,0,0.22)' },
      'C': { fg:'#0099CC', bg:'rgba(0,0,0,0.22)' },
      'X': { fg:'#CC4400', bg:'rgba(0,0,0,0.22)' },
      'Y': { fg:'#44CC00', bg:'rgba(0,0,0,0.22)' },
      'Z': { fg:'#6600CC', bg:'rgba(0,0,0,0.22)' }
    };
    
    actionButtons.forEach(sym => {
      const labelNode = allElements.find(e => (e.textContent || '').trim() === sym);
      if (!labelNode) return;
      const box = getRootBox(labelNode);
      if (!box || box.__segaPressBound) return;
      
      const setPressed = () => {
        const c = pressedColors[sym];
        box.style.borderColor = c.fg;
        box.style.color = c.fg;
        box.style.backgroundColor = c.bg;
        box.style.filter = 'brightness(0.9)';
        box.style.transform = 'scale(0.96)';
      };
      
      const setReleased = () => {
        const c = defaultColors[sym];
        box.style.borderColor = c.fg;
        box.style.color = c.fg;
        box.style.backgroundColor = c.bg;
        box.style.filter = '';
        box.style.transform = '';
      };
      
      box.addEventListener('pointerdown', setPressed);
      box.addEventListener('pointerup', setReleased);
      box.addEventListener('pointercancel', setReleased);
      box.addEventListener('pointerleave', setReleased);
      box.addEventListener('touchstart', setPressed, { passive:true });
      box.addEventListener('touchend', setReleased);
      box.addEventListener('mousedown', setPressed);
      box.addEventListener('mouseup', setReleased);
      
      box.__segaPressBound = true;
    });
  }
  
  function run() {
    applyStyles();
    addPressEffects();
  }
  
  run();
  
  // Watch for gamepad redraws
  new MutationObserver(run).observe(document.body, { childList: true, subtree: true });
  addEventListener('resize', run);
  addEventListener('orientationchange', run);
}

// Initialize
styleSegaButtons();

console.log('[SEGA] Custom gamepad configured - 6-button layout');

})();

