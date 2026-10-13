const itensPedido = document.getElementById('itens-pedido');
document.getElementById('adicionar-item').addEventListener('click', function () {
    if (itensPedido.children.length >= 50) return;
    const item = itensPedido.firstElementChild.cloneNode(true);
    item.querySelector('select').value = '';
    item.querySelector('input').value = '1';
    itensPedido.appendChild(item);
});
itensPedido.addEventListener('click', function (event) {
    if (event.target.classList.contains('remover-item') && itensPedido.children.length > 1) {
        event.target.closest('.item-pedido').remove();
    }
});
