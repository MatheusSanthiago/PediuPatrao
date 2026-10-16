
        // Máscara de CEP
        document.getElementById('cep').addEventListener('input', function () {
            let v = this.value.replace(/\D/g, '');
            if (v.length > 5) v = v.slice(0, 5) + '-' + v.slice(5, 8);
            this.value = v;

            // Busca automática ao completar 9 caracteres (00000-000)
            if (this.value.length === 9) buscarCep();
        });

        // Busca ao clicar no botão
        document.getElementById('btnBuscarCep').addEventListener('click', buscarCep);

        async function buscarCep() {
            const cep = document.getElementById('cep').value.replace(/\D/g, '');
            const erro = document.getElementById('cepErro');
            erro.classList.add('d-none');

            if (cep.length !== 8) return;

            try {
                const response = await fetch(`https://viacep.com.br/ws/${cep}/json/`);
                const data = await response.json();

                if (data.erro) {
                    erro.classList.remove('d-none');
                    return;
                }

                document.getElementById('logradouro').value = data.logradouro || '';
                document.getElementById('bairro').value     = data.bairro     || '';
                document.getElementById('cidade').value     = data.localidade  || '';
                document.getElementById('estado').value     = data.uf          || '';

                // Foca no campo número após preencher
                document.getElementById('numero').focus();

            } catch (e) {
                erro.textContent = 'Erro ao buscar o CEP. Tente novamente.';
                erro.classList.remove('d-none');
            }
        }

        // Máscara de telefone
        document.getElementById('telefone').addEventListener('input', function () {
            let v = this.value.replace(/\D/g, '');
            if (v.length <= 10) {
                v = v.replace(/(\d{2})(\d{4})(\d{0,4})/, '($1) $2-$3');
            } else {
                v = v.replace(/(\d{2})(\d{5})(\d{0,4})/, '($1) $2-$3');
            }
            this.value = v;
        });
