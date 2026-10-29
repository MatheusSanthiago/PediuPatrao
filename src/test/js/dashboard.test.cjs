const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const raiz = path.resolve(__dirname, '../../..');
const codigo = fs.readFileSync(path.join(raiz, 'src/main/resources/static/js/dashboard.js'), 'utf8');
const respostas = {};
for (const recurso of ['resumo', 'vendas', 'status', 'produtos']) {
    respostas[recurso] = JSON.parse(fs.readFileSync(path.join(raiz, 'docs/evidencias/dashboard-' + recurso + '.json'), 'utf8').replace(/^\uFEFF/, ''));
}

// simula apenas os elementos usados pelo painel, sem abrir um navegador
class Elemento {
    constructor() {
        this.textContent = '';
        this.value = '';
        this.disabled = false;
        this.children = [];
        this.dataset = { api: '/api/dashboard/' };
        this.eventos = {};
        const classes = new Set(['d-none']);
        this.classList = {
            add: valor => classes.add(valor),
            remove: valor => classes.delete(valor),
            contains: valor => classes.has(valor),
            toggle: (valor, esconder) => esconder ? classes.add(valor) : classes.delete(valor)
        };
    }
    appendChild(elemento) { this.children.push(elemento); }
    replaceChildren() { this.children = []; }
    addEventListener(nome, funcao) { this.eventos[nome] = funcao; }
}

async function abrir() {
    const elementos = {};
    const chamadas = [];
    const graficos = [];
    const contexto = vm.createContext({
        document: {
            getElementById: id => elementos[id] || (elementos[id] = new Elemento()),
            createElement: () => new Elemento()
        },
        Chart: function (elemento, configuracao) {
            graficos.push(configuracao);
            this.destroy = () => {};
        },
        fetch: async url => {
            chamadas.push(url);
            const recurso = url.split('?')[0].split('/').pop();
            return { ok: true, status: 200, json: async () => structuredClone(respostas[recurso]) };
        },
        Intl, URLSearchParams, AbortController, setTimeout, clearTimeout
    });
    vm.runInContext(codigo, contexto);
    await new Promise(setImmediate);
    return { elementos, chamadas, graficos, contexto };
}

test('carrega quatro endpoints e mostra os valores recebidos e os avisos', async () => {
    const { elementos, chamadas, graficos } = await abrir();
    assert.equal(chamadas.length, 4);
    assert.match(elementos['total-vendido'].textContent, /234,08/);
    assert.match(elementos['ticket-medio'].textContent, /58,52/);
    assert.equal(elementos['pedidos-finalizados'].textContent, 4);
    assert.equal(elementos['itens-vendidos'].textContent, 11);
    assert.equal(elementos['ranking-produtos'].children.length, 3);
    assert.match(elementos['aviso-dashboard'].textContent, /sem data/);
    assert.match(elementos['aviso-dashboard'].textContent, /incompletos/);
    assert.equal(graficos.length, 2);
    assert.equal(graficos[0].data.datasets[0].data[0], 48.08);
    assert.equal(elementos['conteudo-dashboard'].classList.contains('d-none'), false);
    assert.equal(elementos['campos-dashboard'].disabled, false);
});

test('envia o mesmo filtro aos quatro endpoints ao atualizar', async () => {
    const { elementos, chamadas, contexto } = await abrir();
    elementos['data-inicial'].value = '2026-10-25';
    elementos['data-final'].value = '2026-10-27';
    elementos['filtros-dashboard'].eventos.submit({ preventDefault() {} });
    await new Promise(setImmediate);
    assert.equal(chamadas.length, 8);
    assert.ok(chamadas.slice(4).every(url => url.endsWith('?dataInicial=2026-10-25&dataFinal=2026-10-27')));
    assert.equal(vm.runInContext('campos.disabled', contexto), false);
});

for (const [status, mensagem] of [[401, /sessão terminou/], [403, /não tem acesso/], [400, /Período inválido/], [503, /Tente novamente/]]) {
    test('trata HTTP ' + status + ' e esconde os valores anteriores', async () => {
        const { elementos, contexto } = await abrir();
        contexto.fetch = async () => ({ ok: false, status, json: async () => status === 400 ? { mensagem: 'Período inválido' } : {} });
        await vm.runInContext('atualizarPainel(false)', contexto);
        assert.match(elementos['erro-dashboard'].textContent, mensagem);
        assert.equal(elementos['conteudo-dashboard'].classList.contains('d-none'), true);
        assert.equal(elementos['campos-dashboard'].disabled, false);
        assert.equal(elementos['atualizacao-dashboard'].textContent, '');
    });
}

test('trata falha de conexão', async () => {
    const { elementos, contexto } = await abrir();
    vm.runInContext('fetch = async function () { throw new TypeError("Failed to fetch"); }', contexto);
    await vm.runInContext('atualizarPainel(false)', contexto);
    assert.match(elementos['erro-dashboard'].textContent, /Confira a conexão/);
});

test('trata tempo limite e permite tentar de novo', async () => {
    const { elementos, contexto } = await abrir();
    contexto.setTimeout = funcao => setTimeout(funcao, 1);
    contexto.fetch = (url, opcoes) => new Promise((resolve, reject) => {
        opcoes.signal.addEventListener('abort', () => reject(Object.assign(new Error(), { name: 'AbortError' })));
    });
    await vm.runInContext('atualizarPainel(false)', contexto);
    assert.match(elementos['erro-dashboard'].textContent, /demorou demais/);
    assert.equal(elementos['campos-dashboard'].disabled, false);
});

test('mostra período vazio sem deixar ranking da consulta anterior', async () => {
    const { elementos, contexto } = await abrir();
    contexto.fetch = async url => {
        const recurso = url.split('?')[0].split('/').pop();
        const resposta = structuredClone(respostas[recurso]);
        resposta.pedidosNoPeriodo = 0;
        resposta.pedidosFinalizadosIncompletos = 0;
        resposta.dados = recurso === 'resumo' ? { totalVendido: 0, pedidosFinalizados: 0, ticketMedio: 0, itensVendidos: 0 }
            : recurso === 'status' ? { FINALIZADO: 0 } : [];
        return { ok: true, status: 200, json: async () => resposta };
    };
    await vm.runInContext('atualizarPainel(false)', contexto);
    assert.equal(elementos['vazio-dashboard'].classList.contains('d-none'), false);
    assert.equal(elementos['ranking-produtos'].children.length, 1);
    assert.match(elementos['ranking-produtos'].children[0].children[1].textContent, /Nenhum produto/);
});
