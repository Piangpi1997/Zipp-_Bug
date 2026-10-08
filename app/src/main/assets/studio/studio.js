let nodes = [];
let selected = null;
let sequence = 0;
const canvas = document.querySelector('#canvas');

function add(type) {
  const node = {
    id: 'n' + (++sequence),
    type: type,
    label: type,
    x: 24,
    y: 24 + sequence * 44,
    color: '#ff6b00',
    radius: 12
  };
  nodes.push(node);
  render();
}

function render() {
  canvas.innerHTML = '';

  nodes.forEach(function(node) {
    const element = document.createElement('div');
    element.className = 'node' + (selected === node.id ? ' sel' : '');
    element.textContent = node.label;
    element.style.left = node.x + 'px';
    element.style.top = node.y + 'px';
    element.style.background = node.color;
    element.style.borderRadius = node.radius + 'px';

    element.onclick = function() {
      selected = node.id;
      document.querySelector('#label').value = node.label;
      document.querySelector('#color').value = node.color;
      document.querySelector('#radius').value = node.radius;
      render();
    };

    let offsetX = 0;
    let offsetY = 0;

    element.onpointerdown = function(event) {
      element.setPointerCapture(event.pointerId);
      offsetX = event.offsetX;
      offsetY = event.offsetY;
    };

    element.onpointermove = function(event) {
      if (!element.hasPointerCapture(event.pointerId)) return;
      node.x = event.offsetX + element.offsetLeft - offsetX;
      node.y = event.offsetY + element.offsetTop - offsetY;
      element.style.left = node.x + 'px';
      element.style.top = node.y + 'px';
    };

    canvas.appendChild(element);
  });

  document.querySelector('#schema').textContent =
    JSON.stringify(nodes, null, 2);
}

function applyChanges() {
  const node = nodes.find(function(item) {
    return item.id === selected;
  });
  if (!node) return;

  node.label = document.querySelector('#label').value;
  node.color = document.querySelector('#color').value;
  node.radius = Number(document.querySelector('#radius').value);
  render();
}

function save() {
  let html = '<main>';

  nodes.forEach(function(node) {
    html += '<div style="position:absolute;left:' +
      node.x + 'px;top:' + node.y +
      'px;background:' + node.color +
      ';border-radius:' + node.radius +
      'px;padding:12px">' +
      node.label + '</div>';
  });

  html += '</main>';

  if (window.ZipBug) {
    ZipBug.save(
      'screen-' + Date.now(),
      JSON.stringify(nodes),
      html
    );
  }
}

add('Button');
