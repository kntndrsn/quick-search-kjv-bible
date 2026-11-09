function upperAscii(text) {
  let out = '';
  for (const c of text) {
    const code = c.charCodeAt(0);
    out += code >= 97 && code <= 122 ? String.fromCharCode(code - 32) : c;
  }
  return out;
}

export function plainVerseText(raw) {
  let out = '';
  let inDivineName = false;
  for (const char of raw) {
    if (char === '\u2039' || char === '\u203A' || char === '[' || char === ']') continue;
    if (char === '\u2E22') {
      inDivineName = true;
      continue;
    }
    if (char === '\u2E23') {
      inDivineName = false;
      continue;
    }
    out += inDivineName ? upperAscii(char) : char;
  }
  return out;
}

export function headingLabel(heading) {
  return heading.letter ? `${heading.letter} ${heading.text}` : heading.text;
}

/** Parts mirror buildStyledVerseAnnotatedString: christ, italic, and small-caps divine names. */
export function styledParts(raw) {
  const parts = [];
  let buffer = '';
  let inChrist = false;
  let inBracket = false;
  let inDivine = false;

  function flush() {
    if (!buffer) return;
    if (inDivine) {
      parts.push({
        text: upperAscii(buffer[0]),
        christ: inChrist,
        italic: inBracket,
        small: false
      });
      if (buffer.length > 1) {
        parts.push({
          text: upperAscii(buffer.slice(1)),
          christ: inChrist,
          italic: inBracket,
          small: true
        });
      }
    } else {
      parts.push({ text: buffer, christ: inChrist, italic: inBracket, small: false });
    }
    buffer = '';
  }

  for (const char of raw) {
    if (char === '\u2039') {
      flush();
      inChrist = true;
    } else if (char === '\u203A') {
      flush();
      inChrist = false;
    } else if (char === '[') {
      flush();
      inBracket = true;
    } else if (char === ']') {
      flush();
      inBracket = false;
    } else if (char === '\u2E22') {
      flush();
      inDivine = true;
    } else if (char === '\u2E23') {
      flush();
      inDivine = false;
    } else {
      buffer += char;
    }
  }
  flush();
  return parts;
}

export function appendStyled(parent, raw) {
  for (const part of styledParts(raw)) {
    const node = document.createElement('span');
    if (part.christ) node.classList.add('christ');
    if (part.italic) node.classList.add('added');
    if (part.small) node.classList.add('smallcaps');
    node.textContent = part.text;
    if (!part.christ && !part.italic && !part.small) parent.append(part.text);
    else parent.append(node);
  }
}

export function appendHighlighted(parent, plain, pattern) {
  if (!pattern) {
    parent.append(plain);
    return;
  }
  let cursor = 0;
  for (const match of plain.matchAll(pattern)) {
    const start = match.index ?? 0;
    if (start > cursor) parent.append(plain.slice(cursor, start));
    const mark = document.createElement('mark');
    mark.textContent = match[0];
    parent.append(mark);
    cursor = start + match[0].length;
  }
  if (cursor < plain.length) parent.append(plain.slice(cursor));
}
