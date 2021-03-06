package br.com.leguimas.kartero.importer.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import br.com.leguimas.kartero.KarteroException;
import br.com.leguimas.kartero.importer.entity.Bairro;
import br.com.leguimas.kartero.importer.entity.Localidade;
import br.com.leguimas.kartero.importer.entity.Logradouro;

public class CorreiosParser {
	private static final String ARQUIVO_DE_CONFIGURACOES = "kartero.database";

	private static final String LOCALIDADE_FILE = "/LOG_LOCALIDADE.TXT";
	private static final String BAIRRO_FILE = "/LOG_BAIRRO.TXT";
	private static final String LOGRADOURO_FILE = "/LOG_LOGRADOURO_XX.TXT";

	private static final String[] UNIDADES_FEDERATIVAS = new String[] { "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES",
			"GO", "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE",
			"TO" };

	private List<Localidade> localidades;
	private List<Bairro> bairros;
	private List<Logradouro> logradouros;

	public List<Localidade> getLocalidades() {
		return localidades;
	}

	public List<Bairro> getBairros() {
		return bairros;
	}

	public List<Logradouro> getLogradouros() {
		return logradouros;
	}

	public void parseFiles(String basePath) {
		localidades = parseLocalidades(basePath);
		bairros = parseBairros(basePath);
		logradouros = parseLogradouros(basePath);
	}

	private List<Logradouro> parseLogradouros(String basePath) {
		List<Logradouro> ret = new ArrayList<Logradouro>();
		List<String[]> file;

		LineConsummer consummer = new LineConsummer();

		for (String uf : UNIDADES_FEDERATIVAS) {
			file = readFile(basePath + LOGRADOURO_FILE.replace("XX", uf));

			System.out.println("Reading logradouros from: " + uf);
			for (String[] line : file) {
				consummer.setLine(line);

				Logradouro logradouro = new Logradouro();
				logradouro.consumeLine(consummer);
				ret.add(logradouro);
			}
		}

		return ret;
	}

	private List<Bairro> parseBairros(String basePath) {
		List<Bairro> ret = new ArrayList<Bairro>();
		List<String[]> file = readFile(basePath + BAIRRO_FILE);

		LineConsummer consummer = new LineConsummer();
		for (String[] line : file) {
			consummer.setLine(line);

			Bairro bairro = new Bairro();
			bairro.consumeLine(consummer);
			ret.add(bairro);
		}

		return ret;
	}

	private List<Localidade> parseLocalidades(String basePath) {

		List<Localidade> ret = new ArrayList<Localidade>();
		List<String[]> file = readFile(basePath + LOCALIDADE_FILE);

		LineConsummer consummer = new LineConsummer();
		for (String[] line : file) {
			consummer.setLine(line);

			Localidade localidade = new Localidade();
			localidade.consumeLine(consummer);
			ret.add(localidade);
		}

		return ret;
	}

	private List<String[]> readFile(String path) {
		List<String[]> ret = new ArrayList<String[]>();

		BufferedReader br = null;
		try {
		    File f = new File(path);
			br = new BufferedReader(new BufferedReader(
			new InputStreamReader(new FileInputStream(f), "ISO-8859-1")));
			StringBuilder sb = new StringBuilder();
			String line = br.readLine();

			while (line != null) {
				sb.append(line);
				ret.add(line.split("@"));
				line = br.readLine();
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			if (br != null)
				try {
					br.close();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
		}

		return ret;
	}

	public void saveToBase() {
		Connection connection = getConnection();

		try {
			saveLocalidades(connection);
			saveBairros(connection);
			saveLogradouros(connection);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			closeConnection(connection);
		}
	}

	private Connection getConnection() {
		Properties arquivoDeConfiguracoes = new Properties();
		try {
			arquivoDeConfiguracoes.load(Thread.currentThread().getContextClassLoader()
					.getResourceAsStream(ARQUIVO_DE_CONFIGURACOES));

			Class.forName(arquivoDeConfiguracoes.getProperty("kartero.classeJDBC"));

			String url = arquivoDeConfiguracoes.getProperty("kartero.urlJDBC");
			String user = arquivoDeConfiguracoes.getProperty("kartero.usuario");
			String pass = arquivoDeConfiguracoes.getProperty("kartero.senha");
			Connection conn = DriverManager.getConnection(url, user, pass);
			return conn;
		} catch (IOException | ClassNotFoundException | SQLException e) {
			e.printStackTrace();
			throw new KarteroException(e, "Nao foi possivel encontrar o arquivo de configuracoes ("
					+ ARQUIVO_DE_CONFIGURACOES + "). Verifique se o mesmo encontra-se no classpath da aplicacao. ");
		}
	}

	/*
	 * log_logradouro ( loc_nu_sequencial INTEGER, log_no VARCHAR(70), bai_nu_sequencial_ini INTEGER, cep VARCHAR(16),
	 * log_tipo_logradouro VARCHAR(72));
	 */
	private static final String SQL_LOGRADOUROS = "INSERT INTO log_logradouro (loc_nu_sequencial, log_no, bai_nu_sequencial_ini, cep, log_tipo_logradouro) VALUES (?, ?, ?, ?, ?)";

	private void saveLogradouros(Connection connection) throws SQLException {
		PreparedStatement prepareStatement;
		int i = 0;
		for (Logradouro logradouro : logradouros) {
			System.out.println("Saving logradouro " + i++ + " from " + logradouros.size());
			prepareStatement = connection.prepareStatement(SQL_LOGRADOUROS);
			prepareStatement.setInt(1, logradouro.getLocNu());
			prepareStatement.setString(2, logradouro.getLogNo());
			prepareStatement.setInt(3, logradouro.getBaiNuIni());
			prepareStatement.setString(4, logradouro.getCep());
			prepareStatement.setString(5, logradouro.getTloTx());
			prepareStatement.execute();
			prepareStatement.close();
		}
	}

	/*
	 * log_bairro ( bai_nu_sequencial INTEGER, bai_no VARCHAR(72) NOT NULL );
	 */
	private static final String SQL_BAIRROS = "INSERT INTO log_bairro (bai_nu_sequencial, bai_no) VALUES (?, ?)";

	private void saveBairros(Connection connection) throws SQLException {
		PreparedStatement prepareStatement;
		int i = 0;
		for (Bairro bairro : bairros) {
			System.out.println("Saving bairro " + i++ + " from " + bairros.size());
			prepareStatement = connection.prepareStatement(SQL_BAIRROS);
			prepareStatement.setInt(1, bairro.getBaiNu());
			prepareStatement.setString(2, bairro.getBaiNo());
			prepareStatement.execute();
			prepareStatement.close();
		}
	}

	/*
	 * log_localidade ( loc_nu_sequencial INTEGER, loc_no VARCHAR(60), ufe_sg VARCHAR(2) );
	 */
	private static final String SQL_LOCALIDADES = "INSERT INTO log_localidade (loc_nu_sequencial, loc_no, ufe_sg, cep) VALUES (?, ?, ?, ?)";

	private void saveLocalidades(Connection conection) throws SQLException {
		PreparedStatement prepareStatement;
		int i = 0;
		for (Localidade localidade : localidades) {
			System.out.println("Saving localidade " + i++ + " from " + localidades.size());
			prepareStatement = conection.prepareStatement(SQL_LOCALIDADES);
			prepareStatement.setInt(1, localidade.getLocNu());
			prepareStatement.setString(2, localidade.getLocNo());
			prepareStatement.setString(3, localidade.getUfeSg());
			prepareStatement.setString(4, localidade.getCep());
			prepareStatement.execute();
			prepareStatement.close();
		}
	}

	private void closeConnection(Connection conexao) {
		if (conexao != null) {
			try {
				conexao.close();
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}
	}
}
