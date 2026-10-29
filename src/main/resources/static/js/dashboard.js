const painel = document.getElementById('dashboard');
const formulario = document.getElementById('filtros-dashboard');
const campos = document.getElementById('campos-dashboard');
const inicio = document.getElementById('data-inicial');
const fim = document.getElementById('data-final');
const erroPainel = document.getElementById('erro-dashboard');
const conteudoPainel = document.getElementById('conteudo-dashboard');
const moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
let graficoVendas;
let graficoQuantidade;

function adicionarLinha(tabela, valores) {
    const linha = document.createElement('tr');
    valores.forEach(function (valor) {
        const celula = document.createElement('td');
        celula.textContent = valor;
        linha.appendChild(celula);
    });
    tabela.appendChild(linha);
}

function dataBrasileira(data) {
    return data.split('-').reverse().join('/');
}

async function consultar(recurso, parametros, sinal) {
    const resposta = await fetch(painel.dataset.api + recurso + parametros, { cache: 'no-store', signal: sinal });
    if (resposta.status === 401) throw new Error('Sua sessão terminou. Entre novamente no sistema.');
    if (resposta.status === 403) throw new Error('Seu perfil não tem acesso ao dashboard.');
    if (!resposta.ok) {
        const erro = await resposta.json().catch(function () { return {}; });
        throw new Error(erro.mensagem || 'Não foi possível consultar os dados. Tente novamente.');
    }
    return resposta.json();
}

function mostrarDados(resumo, vendas, situacao, produtos) {
    inicio.value = resumo.dataInicial;
    fim.value = resumo.dataFinal;
    document.getElementById('total-vendido').textContent = moeda.format(resumo.dados.totalVendido);
    document.getElementById('pedidos-finalizados').textContent = resumo.dados.pedidosFinalizados;
    document.getElementById('ticket-medio').textContent = moeda.format(resumo.dados.ticketMedio);
    document.getElementById('itens-vendidos').textContent = resumo.dados.itensVendidos;
    document.getElementById('total-pedidos-periodo').textContent = situacao.pedidosNoPeriodo + ' pedidos no período';
    document.getElementById('vazio-dashboard').classList.toggle('d-none', resumo.pedidosNoPeriodo !== 0);
    document.getElementById('sem-vendas-dashboard').classList.toggle('d-none', resumo.dados.pedidosFinalizados !== 0 || resumo.pedidosNoPeriodo === 0);
    const avisos = [];
    if (resumo.pedidosSemData > 0) avisos.push(resumo.pedidosSemData + ' pedidos do banco estão sem data de entrada e não aparecem nos períodos.');
    if (resumo.pedidosFinalizadosIncompletos > 0) avisos.push(resumo.pedidosFinalizadosIncompletos + ' pedidos finalizados do período estão com dados incompletos e não entram nos valores, gráficos ou ranking.');
    const aviso = document.getElementById('aviso-dashboard');
    aviso.textContent = avisos.join(' ');
    aviso.classList.toggle('d-none', avisos.length === 0);

    const nomes = { RECEBIDO: 'Recebido', EM_PREPARACAO: 'Em preparação', PRONTO: 'Pronto',
        SAIU_PARA_ENTREGA: 'Saiu para entrega', RETIRADO: 'Retirado', FINALIZADO: 'Finalizado', CANCELADO: 'Cancelado', OUTROS: 'Outros / não informado' };
    const tabelaStatus = document.getElementById('situacao-pedidos');
    tabelaStatus.replaceChildren();
    Object.keys(situacao.dados).forEach(function (status) {
        adicionarLinha(tabelaStatus, [nomes[status] || status, situacao.dados[status]]);
    });
    const ranking = document.getElementById('ranking-produtos');
    ranking.replaceChildren();
    produtos.dados.forEach(function (produto, indice) {
        adicionarLinha(ranking, [indice + 1, produto.nome, produto.quantidade]);
    });
    if (produtos.dados.length === 0) adicionarLinha(ranking, ['—', 'Nenhum produto vendido no período', '—']);
    const tabelaVendas = document.getElementById('vendas-diarias');
    tabelaVendas.replaceChildren();
    vendas.dados.forEach(function (dia) {
        adicionarLinha(tabelaVendas, [dataBrasileira(dia.data), moeda.format(dia.totalVendido), dia.pedidosFinalizados]);
    });

    conteudoPainel.classList.remove('d-none');
    if (graficoVendas) graficoVendas.destroy();
    if (graficoQuantidade) graficoQuantidade.destroy();
    const datas = vendas.dados.map(function (dia) { return dataBrasileira(dia.data); });
    graficoVendas = new Chart(document.getElementById('grafico-vendas'), {
        type: 'line',
        data: { labels: datas, datasets: [{ label: 'Vendas em R$', borderColor: '#0d6efd', backgroundColor: 'rgba(13,110,253,0.08)',
            lineTension: 0, data: vendas.dados.map(function (dia) { return dia.totalVendido; }) }] },
        options: { responsive: true, maintainAspectRatio: false,
            scales: { yAxes: [{ ticks: { beginAtZero: true, callback: function (valor) { return moeda.format(valor); } } }] },
            tooltips: { callbacks: { label: function (item) { return moeda.format(item.yLabel); } } } }
    });
    graficoQuantidade = new Chart(document.getElementById('grafico-quantidade'), {
        type: 'bar',
        data: { labels: datas, datasets: [{ label: 'Pedidos finalizados', backgroundColor: '#198754',
            data: vendas.dados.map(function (dia) { return dia.pedidosFinalizados; }) }] },
        options: { responsive: true, maintainAspectRatio: false,
            scales: { yAxes: [{ ticks: { beginAtZero: true, precision: 0 } }] } }
    });
}

async function atualizarPainel(primeiraConsulta) {
    if (campos.disabled) return;
    erroPainel.classList.add('d-none');
    conteudoPainel.classList.add('d-none');
    document.getElementById('atualizacao-dashboard').textContent = '';
    document.getElementById('carregando-dashboard').classList.remove('d-none');
    campos.disabled = true;
    const controle = new AbortController();
    const tempoLimite = setTimeout(function () { controle.abort(); }, 15000);
    try {
        if (typeof Chart === 'undefined') throw new Error('Não foi possível carregar os gráficos. Recarregue a página.');
        const parametros = primeiraConsulta ? '' : '?' + new URLSearchParams({ dataInicial: inicio.value, dataFinal: fim.value });
        const respostas = await Promise.all(['resumo', 'vendas', 'status', 'produtos'].map(function (recurso) {
            return consultar(recurso, parametros, controle.signal);
        }));
        mostrarDados(respostas[0], respostas[1], respostas[2], respostas[3]);
        document.getElementById('atualizacao-dashboard').textContent = 'Período: ' + dataBrasileira(respostas[0].dataInicial)
            + ' a ' + dataBrasileira(respostas[0].dataFinal) + '. Atualizado em '
            + new Date().toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo' }) + ' (Brasília).';
    } catch (erro) {
        controle.abort();
        conteudoPainel.classList.add('d-none');
        erroPainel.textContent = erro.name === 'AbortError' ? 'A consulta demorou demais. Tente atualizar novamente.'
            : erro instanceof TypeError ? 'Não foi possível conectar ao sistema. Confira a conexão e tente novamente.' : erro.message;
        erroPainel.classList.remove('d-none');
    } finally {
        clearTimeout(tempoLimite);
        campos.disabled = false;
        document.getElementById('carregando-dashboard').classList.add('d-none');
    }
}

formulario.addEventListener('submit', function (event) {
    event.preventDefault();
    atualizarPainel(false);
});
atualizarPainel(true);
