const VERSE_MARKER_CHARS = '\u2039\u203A[]\u2E22\u2E23,;';
const JOINING_CHARS = '-\u2013';

function acceptChar(sb, pending, c, allowColon) {
  const kept = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || (allowColon && c === ':');
  if (kept) {
    if (pending.space && sb.length > 0) sb.push(' ');
    pending.space = false;
    sb.push(c);
  } else {
    pending.space = true;
  }
}

function clean(text, allowColon, verseMode, stripVerseMarkers = false) {
  const sb = [];
  const pending = { space: false };
  for (const c of text) {
    if (stripVerseMarkers && VERSE_MARKER_CHARS.includes(c)) continue;
    if (verseMode && JOINING_CHARS.includes(c)) continue;
    if (c === '\u00E6' || c === '\u00C6') {
      acceptChar(sb, pending, 'a', allowColon);
      acceptChar(sb, pending, 'e', allowColon);
    } else if (c.charCodeAt(0) < 128) {
      const lower = c >= 'A' && c <= 'Z' ? String.fromCharCode(c.charCodeAt(0) + 32) : c;
      acceptChar(sb, pending, lower, allowColon);
    } else {
      for (const lc of c.toLowerCase()) acceptChar(sb, pending, lc, allowColon);
    }
  }
  return sb.join('');
}

function matchOrdinal(s, words) {
  for (const word of words) {
    if (!s.startsWith(word)) continue;
    const end = word.length;
    if (end >= s.length) return -1;
    const next = s[end];
    if (next === ' ') return end + 1;
    if (next >= 'a' && next <= 'z') return end;
    return -1;
  }
  return -1;
}

function rewriteLeadingOrdinal(cleaned) {
  if (!cleaned) return cleaned;
  const third = matchOrdinal(cleaned, ['3rd', 'third', 'iii']);
  if (third >= 0) return `3 ${cleaned.slice(third)}`;
  const second = matchOrdinal(cleaned, ['2nd', 'second', 'ii']);
  if (second >= 0) return `2 ${cleaned.slice(second)}`;
  const first = matchOrdinal(cleaned, ['1st', 'first', 'i']);
  if (first >= 0) return `1 ${cleaned.slice(first)}`;
  return cleaned;
}

export function normalize(text) {
  return rewriteLeadingOrdinal(clean(text, true, false, false));
}

export function normalizeVerseQuery(text) {
  return clean(text, false, true, false);
}

export function normalizeVerseText(text) {
  return clean(text, false, true, true);
}

export function compactNormalized(normalized) {
  return normalized.includes(' ') ? normalized.replaceAll(' ', '') : normalized;
}

function soundexDigit(char) {
  switch (char) {
    case 'b':
    case 'f':
    case 'p':
    case 'v':
      return '1';
    case 'c':
    case 'g':
    case 'j':
    case 'k':
    case 'q':
    case 's':
    case 'x':
    case 'z':
      return '2';
    case 'd':
    case 't':
      return '3';
    case 'l':
      return '4';
    case 'm':
    case 'n':
      return '5';
    case 'r':
      return '6';
    default:
      return '0';
  }
}

export function soundexOfCompact(compact) {
  if (!compact) return '';
  const out = ['0', '0', '0', '0'];
  out[0] = compact[0] >= 'a' && compact[0] <= 'z'
    ? String.fromCharCode(compact[0].charCodeAt(0) - 32)
    : compact[0].toUpperCase();
  let size = 1;
  let last = '\0';
  for (let i = 1; i < compact.length && size < 4; i += 1) {
    const digit = soundexDigit(compact[i]);
    if (digit !== '0' && digit !== last) {
      out[size] = digit;
      size += 1;
      last = digit;
    }
  }
  return out.join('');
}

export function soundex(text) {
  return soundexOfCompact(compactNormalized(normalize(text)));
}

export function highlightPatternFor(token) {
  let pattern = '';
  for (let i = 0; i < token.length; i += 1) {
    if (i > 0) pattern += '[-\u2013]?';
    if (token[i] === 'a' && token[i + 1] === 'e') {
      pattern += '(?:[aA][-\u2013]?[eE]|[\u00E6\u00C6])';
      i += 1;
    } else {
      const c = token[i];
      if (c >= 'a' && c <= 'z') {
        pattern += `[${c}${String.fromCharCode(c.charCodeAt(0) - 32)}]`;
      }
      else pattern += c.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    }
  }
  return pattern;
}
