package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.util.List;
import java.math.BigDecimal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ProdutoService {
    private final ProdutoRepository produtoRepository;

    public ProdutoService(ProdutoRepository produtoRepository) {
        this.produtoRepository = produtoRepository;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public Produto novoProduto(Produto produto) {
        produto.setId(null);
        return salvar(produto);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE', 'ATENDENTE')")
    public List<Produto> listarProdutos() {
        return produtoRepository.findAll();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public Produto buscarPorId(String id) {
        return produtoRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Produto não encontrado."));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public void excluir(String id) {
        produtoRepository.delete(buscarPorId(id));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public Produto salvar(Produto produto) {
        if (produto.getNome() == null || produto.getNome().isBlank() || produto.getNome().length() > 120
                || produto.getTipo() == null || produto.getTipo().isBlank()
                || produto.getPreco() == null || !Double.isFinite(produto.getPreco())
                || produto.getPreco() <= 0 || produto.getPreco() > 100000
                || BigDecimal.valueOf(produto.getPreco()).stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Informe nome, tipo e preço de R$ 0,01 a R$ 100.000, com até duas casas decimais.");
        }
        if (produto.getId() != null && !produtoRepository.existsById(produto.getId())) {
            throw new IllegalArgumentException("Produto não encontrado para atualização.");
        }
        produto.setAtivo(Boolean.TRUE.equals(produto.getAtivo()));
        return produtoRepository.save(produto);
    }
}
