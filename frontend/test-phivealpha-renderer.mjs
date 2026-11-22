// In-memory Angular renderer for Node unit tests. Browser layout is checked separately.
import { DOCUMENT, ɵIMAGE_CONFIG, RendererFactory2 } from '@angular/core';
import { createApplication } from '@angular/platform-browser';
import { readFileSync } from 'node:fs';
import ts from 'typescript';

export function componentModule(path, imports = {}) {
  const file = new URL(`./src/phivealpha/${path}.ts`, import.meta.url);
  const source = readFileSync(file, 'utf8')
    .replace(/templateUrl: '([^']+)'/, (_, template) =>
      `template: ${JSON.stringify(readFileSync(new URL(template, file), 'utf8'))}`)
    .replace(/  styleUrl: '[^']+',\n/, '');
  const compiled = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.ESNext, experimentalDecorators: true, target: ts.ScriptTarget.ES2022 },
  }).outputText.replace(/(['"])(@angular\/[^'"]+|rxjs|\.[^'"]+)\1/g, (match, quote, specifier) => {
    const url = imports[specifier] ?? (specifier.startsWith('.') ? null : import.meta.resolve(specifier));
    return url ? `${quote}${url}${quote}` : match;
  });
  return `data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`;
}

export function element(tag, text = '') {
  return { tag, tagName: tag.toUpperCase(), text, attrs: {}, children: [], events: {} };
}

function detach(child) {
  if (child.parent) {
    child.parent.children.splice(child.parent.children.indexOf(child), 1);
    child.parent = undefined;
  }
}

const renderer = {
  createElement: tag => element(tag),
  createText: value => element('#text', value),
  createComment: () => element('#comment'),
  appendChild: (parent, child) => renderer.insertBefore(parent, child, null),
  insertBefore(parent, child, sibling) {
    detach(child);
    child.parent = parent;
    const index = sibling ? parent.children.indexOf(sibling) : parent.children.length;
    parent.children.splice(index, 0, child);
  },
  removeChild: (_, child) => detach(child),
  parentNode: child => child.parent ?? null,
  nextSibling: child => child.parent?.children[child.parent.children.indexOf(child) + 1] ?? null,
  selectRootElement: root => root,
  setValue: (child, text) => { child.text = text; },
  setAttribute: (child, name, value) => { child.attrs[name] = value; },
  removeAttribute: (child, name) => { delete child.attrs[name]; },
  setProperty: (child, name, value) => { child[name] = value; },
  addClass: () => {},
  removeClass: () => {},
  listen(child, name, handler) {
    child.events[name] = handler;
    return () => { delete child.events[name]; };
  },
  destroy: () => {},
};

export function descendants(root, predicate) {
  return root.children.flatMap(child => [
    ...(predicate(child) ? [child] : []),
    ...descendants(child, predicate),
  ]);
}

export function content(node) {
  return node.text + node.children.map(content).join('');
}

export function application(providers) {
  return createApplication({
    providers: [
      { provide: DOCUMENT, useValue: { getElementById: () => null } },
      { provide: ɵIMAGE_CONFIG, useValue: { disableImageSizeWarning: true, disableImageLazyLoadWarning: true } },
      { provide: RendererFactory2, useValue: { createRenderer: () => renderer } },
      ...providers,
    ],
  });
}
