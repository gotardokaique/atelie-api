package com.gen.core.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ServiceUtils {

	// ---------- DataUtils ----------
	public LocalDateTime now() {
		return LocalDateTime.now();
	}

	public LocalDate hoje() {
		return LocalDate.now();
	}

	public Instant nowUtc() {
		return Instant.now();
	}

	public String formatarData(LocalDate data, String padrao) {
		return data.format(DateTimeFormatter.ofPattern(padrao));
	}

	public LocalDate parseData(String texto, String padrao) {
		return LocalDate.parse(texto, DateTimeFormatter.ofPattern(padrao));
	}

	public LocalDate adicionarDias(LocalDate data, int dias) {
		return data.plusDays(dias);
	}

	public boolean dataExpirada(LocalDate data) {
		return data.isBefore(hoje());
	}

	public boolean isHoje(LocalDate data) {
		return data.equals(hoje());
	}

	// ---------- ValidationUtils ----------
	private static final Pattern EMAIL_REGEX = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

	public boolean isEmailValido(String email) {
		return email != null && EMAIL_REGEX.matcher(email).matches();
	}

	public boolean isCpfValido(String cpf) {
		if (cpf == null)
			return false;
		String num = cpf.replaceAll("\\D", "");
		if (num.length() != 11 || num.matches("(\\d)\\1{10}"))
			return false;
		return validarCpfDigitos(num);
	}

	private boolean validarCpfDigitos(String num) {
		int[] peso1 = { 10, 9, 8, 7, 6, 5, 4, 3, 2 }, peso2 = { 11, 10, 9, 8, 7, 6, 5, 4, 3, 2 };
		char d1 = calcularDig(num.substring(0, 9), peso1);
		char d2 = calcularDig(num.substring(0, 9) + d1, peso2);
		return num.equals(num.substring(0, 9) + d1 + d2);
	}

	private char calcularDig(String str, int[] peso) {
		int s = 0;
		for (int i = 0; i < str.length(); i++)
			s += Character.getNumericValue(str.charAt(i)) * peso[i];
		int r = s % 11;
		return (r < 2) ? '0' : (char) ('0' + 11 - r);
	}

	public String removerAcentos(String texto) {
		if (texto == null)
			return null;
		String n = Normalizer.normalize(texto, Normalizer.Form.NFD);
		return n.replaceAll("\\p{M}", "");
	}

	public String removerCaracteresEspeciais(String texto) {
		if (texto == null)
			return null;
		return texto.replaceAll("[^\\p{Alnum}\\s]", "");
	}

	// ---------- SecurityUtils ----------
	private static final SecureRandom RAND = new SecureRandom();

	public String gerarTokenAleatorio(int t) {
		String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
		var sb = new StringBuilder(t);
		for (int i = 0; i < t; i++)
			sb.append(chars.charAt(RAND.nextInt(chars.length())));
		return sb.toString();
	}

	public String gerarUUID() {
		return UUID.randomUUID().toString();
	}

	public String hashMD5(String texto) {
		return hash(texto, "MD5");
	}

	public String hashSHA256(String texto) {
		return hash(texto, "SHA-256");
	}

	private String hash(String texto, String alg) {
		try {
			MessageDigest md = MessageDigest.getInstance(alg);
			byte[] d = md.digest(texto.getBytes(StandardCharsets.UTF_8));
			var sb = new StringBuilder();
			for (byte b : d)
				sb.append(String.format("%02x", b));
			return sb.toString();
		} catch (Exception e) {
			throw new RuntimeException("Erro hash " + alg, e);
		}
	}

	public String criptografarAES(String texto, String chaveB64) {
		try {
			byte[] key = Base64.getDecoder().decode(chaveB64);
			SecretKey sk = new SecretKeySpec(key, "AES");
			byte[] iv = new byte[16];
			RAND.nextBytes(iv);
			Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
			c.init(Cipher.ENCRYPT_MODE, sk, new IvParameterSpec(iv));
			byte[] enc = c.doFinal(texto.getBytes(StandardCharsets.UTF_8));
			byte[] combinado = new byte[iv.length + enc.length];
			System.arraycopy(iv, 0, combinado, 0, iv.length);
			System.arraycopy(enc, 0, combinado, iv.length, enc.length);
			return Base64.getEncoder().encodeToString(combinado);
		} catch (Exception e) {
			throw new RuntimeException("Erro cript AES", e);
		}
	}

	public String descriptografarAES(String textoB64, String chaveB64) {
		try {
			byte[] combinado = Base64.getDecoder().decode(textoB64);
			byte[] iv = Arrays.copyOfRange(combinado, 0, 16);
			byte[] enc = Arrays.copyOfRange(combinado, 16, combinado.length);
			SecretKey sk = new SecretKeySpec(Base64.getDecoder().decode(chaveB64), "AES");
			Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
			c.init(Cipher.DECRYPT_MODE, sk, new IvParameterSpec(iv));
			byte[] dec = c.doFinal(enc);
			return new String(dec, StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new RuntimeException("Erro decript AES", e);
		}
	}

	

	private final ObjectMapper mapper = new ObjectMapper();

	public String toJson(Object o) {
		try {
			return mapper.writeValueAsString(o);
		} catch (Exception e) {
			throw new RuntimeException("Erro toJson", e);
		}
	}

	public <T> T fromJson(String json, Class<T> cls) {
		try {
			return mapper.readValue(json, cls);
		} catch (Exception e) {
			throw new RuntimeException("Erro fromJson", e);
		}
	}

	public <T> T converterObjeto(Object ori, Class<T> dst) {
		return fromJson(toJson(ori), dst);
	}

	public File salvarTemporario(byte[] bytes) throws IOException {
		Path tmp = Files.createTempFile("tmp", null);
		Files.write(tmp, bytes);
		return tmp.toFile();
	}

	public byte[] lerArquivo(String caminho) throws IOException {
		return Files.readAllBytes(Paths.get(caminho));
	}

	public boolean deletarTemporario(File f) {
		return f != null && f.exists() && f.delete();
	}

	public String capitalize(String s) {
		if (s == null || s.isEmpty())
			return s;
		return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
	}

	public String limitar(String s, int mx) {
		if (s == null || s.length() <= mx)
			return s;
		return s.substring(0, mx);
	}

	public String preencherComZeros(String s, int tam) {
		if (s == null)
			s = "";
		return String.format("%1$" + tam + "s", s).replace(' ', '0');
	}

	public String converterParaSlug(String s) {
		if (s == null)
			return null;
		String na = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		return na.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	public long tempoExecucao(Runnable r) {
		long ini = System.currentTimeMillis();
		r.run();
		return System.currentTimeMillis() - ini;
	}

	public void executarComTimeout(Runnable r, long timeoutMillis)
			throws TimeoutException, InterruptedException, ExecutionException {
		ExecutorService svc = Executors.newSingleThreadExecutor();
		Future<?> fut = svc.submit(r);
		try {
			fut.get(timeoutMillis, TimeUnit.MILLISECONDS);
		} finally {
			svc.shutdownNow();
		}
	}

	public <E extends Enum<E>> E fromStringEnum(Class<E> cls, String val, E padrao) {
		if (val == null)
			return padrao;
		try {
			return Enum.valueOf(cls, val.trim().toUpperCase());
		} catch (Exception e) {
			return padrao;
		}
	}

	public <E extends Enum<E>> List<E> listarTodosEnum(Class<E> cls) {
		return Arrays.asList(cls.getEnumConstants());
	}

	public void agendarTarefa(Runnable t, Instant quando) {
		scheduler.schedule(t, Date.from(quando));
	}

}
