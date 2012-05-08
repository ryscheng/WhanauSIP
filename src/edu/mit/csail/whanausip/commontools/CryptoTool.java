package edu.mit.csail.whanausip.commontools;

import java.math.BigInteger;
import java.util.Date;
import java.io.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import javax.security.auth.x500.X500Principal;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.x509.extension.SubjectKeyIdentifierStructure;

/**
 * Performs all cryptographic functions,
 * including hashing, digital signatures etc.
 * 
 * @author ryscheng
 * @date 2009/05/07
 */
public class CryptoTool {
	/*********************
	 * LOADING/SAVING KEYS
	 *********************/
	
	/**
	 * Generates a public/private key pair using the default values in WhanauDHTConstants
	 * @return KeyPair = public/private key pair 
	 * @throws NoSuchAlgorithmException - if WhanauDHTConstants.CRYPTO_KEY_ALG 
	 * 										is not a valid algorithm
	 */
	public static KeyPair generateKeyPair() throws NoSuchAlgorithmException{
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance(
														WhanauDHTConstants.CRYPTO_KEY_ALG, 
														WhanauDHTConstants.CRYPTO_PROVIDER);
		keyGen.initialize(WhanauDHTConstants.CRYPTO_KEY_SIZE);
		KeyPair key = keyGen.generateKeyPair();
		return key;
	}
	
	/**
	 * Generates a Java keystore in the proper layout for use with Whanau DHT
	 * The keys are stored as a certificate under the alias - WhanauDHTConstants.CRYPTO_ALIAS
	 * 
	 * @param 	password String = password to lock the KeyStore
	 * @return 	KeyStore 		= keys used by WhanauDHT
	 * @throws KeyStoreException
	 * @throws NoSuchAlgorithmException
	 * @throws CertificateException
	 * @throws IOException
	 * @throws InvalidKeyException
	 * @throws SignatureException
	 * @throws NoSuchProviderException
	 */
	public static KeyStore generateKeyStore(String password) 
										throws KeyStoreException, NoSuchAlgorithmException, 
										CertificateException, IOException, InvalidKeyException, 
										SignatureException, NoSuchProviderException {
		//Generate keys
		KeyStore keyStore = KeyStore.getInstance(WhanauDHTConstants.CRYPTO_KEYSTORE_TYPE);
		KeyPair key = CryptoTool.generateKeyPair();
		PublicKey publicKey = key.getPublic();
		PrivateKey privateKey = key.getPrivate();
		keyStore.load(null,password.toCharArray());
		Security.addProvider(new BouncyCastleProvider());
		//Create a certificate from it
		X509Certificate[] chain = new X509Certificate[1];
		X509V3CertificateGenerator certGen = new X509V3CertificateGenerator();
		X500Principal subjectName = new X500Principal(WhanauDHTConstants.CRYPTO_CERT_NAME);
		certGen.setSerialNumber(new BigInteger(WhanauDHTConstants.CRYPTO_SERIAL_NUM));
		certGen.setIssuerDN(subjectName);
		certGen.setNotBefore(new Date());
		certGen.setNotAfter(new Date());
		certGen.setSubjectDN(subjectName);
		certGen.setPublicKey(publicKey);
		certGen.setSignatureAlgorithm(WhanauDHTConstants.CRYPTO_SIG_ALG);
		certGen.addExtension(X509Extensions.SubjectKeyIdentifier, false, 
									new SubjectKeyIdentifierStructure(key.getPublic()));
		chain[0]= certGen.generate(privateKey, "BC"); // note: private key of CA
		//Store it in the keystore and return
		keyStore.setEntry(WhanauDHTConstants.CRYPTO_ALIAS,
									new KeyStore.PrivateKeyEntry(privateKey, chain),	
									new KeyStore.PasswordProtection(password.toCharArray()));
		return keyStore;
	}
	
	/**
	 * Loads a KeyStore from file for use with WhanauDHT
	 * Must have been saved with CryptoTool.saveKeyStore(...)
	 * 
	 * @param file 		String 	= filename to read from
	 * @param password 	String 	= password to unlock the KeyStre
	 * @return KeyStore 		= keys used by WhanauDHT
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws CertificateException
	 * @throws NoSuchAlgorithmException
	 * @throws KeyStoreException
	 */
	public static KeyStore loadKeyStore(String file, String password) 
										throws FileNotFoundException, IOException, 
											CertificateException, NoSuchAlgorithmException, 
											KeyStoreException{
		KeyStore keyStore = KeyStore.getInstance(WhanauDHTConstants.CRYPTO_KEYSTORE_TYPE);
		FileInputStream keyFile = new FileInputStream(file);
		keyStore.load(keyFile,password.toCharArray());
		keyFile.close();
		return keyStore;
	}
	
	/**
	 * Saves a KeyStore to a file.
	 * Must have been generated by CryptoTool.generateKeyStore(...)
	 * This allows for WhanauDHT identities to stay persistent
	 * 
	 * @param ks 		KeyStore 	= keys to store
	 * @param filename 	String 		= filename/path to store keys to
	 * @param password 	String		= password to unlock KeyStore
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws CertificateException
	 * @throws NoSuchAlgorithmException
	 * @throws KeyStoreException
	 */
	public static void saveKeyStore(KeyStore ks, String filename, String password) 
										throws FileNotFoundException, IOException, 
											CertificateException, NoSuchAlgorithmException, 
											KeyStoreException{
		FileOutputStream file = new FileOutputStream(filename);
		ks.store(file,password.toCharArray());
		file.close();
	}
	
	/**
	 * Returns the encoded public key from a KeyStore object
	 * Keys must be stored under the alias, WhanauDHTConstants.CRYPTO_ALIAS
	 * 
	 * @param ks KeyStore 	= KeyStore to read the public key from
	 * @return byte[] 		= encoded public key
	 * @throws KeyStoreException
	 */
	public static byte[] getPublicKey(KeyStore ks) throws KeyStoreException{
		return ks.getCertificate(WhanauDHTConstants.CRYPTO_ALIAS)
					.getPublicKey().getEncoded();
	}
	
	/**
	 * Performs a SHA1 hash on the encoded public key of a KeyStore
	 * Keys must be stored under the alias, WhanauDHTConstants.CRYPTO_ALIAS
	 * This is generally used for node identification
	 * 
	 * @param ks KeyStore 	= keys to read public key from
	 * @return String 		= SHA1 hash of the public key
	 * @throws NoSuchAlgorithmException
	 * @throws UnsupportedEncodingException
	 * @throws KeyStoreException
	 */
	public static String getPublicKeyHash(KeyStore ks) 
										throws NoSuchAlgorithmException, 
										UnsupportedEncodingException, KeyStoreException{
		return CryptoTool.SHA1toHex(CryptoTool.getPublicKey(ks));
	}
	
	/**
	 * Performs a SHA1 hash on the encoded public key of a KeyStore read from file
	 * Keys must be stored under the alias, WhanauDHTConstants.CRYPTO_ALIAS
	 * This is generally used for node identification
	 * 
	 * @param file	 	String 	= filename/path of KeyStore
	 * @param password 	String 	= password to unlock KeyStore
	 * @return String 			= SHA1 hash of the public key
	 * @throws FileNotFoundException
	 * @throws CertificateException
	 * @throws NoSuchAlgorithmException
	 * @throws KeyStoreException
	 * @throws IOException
	 */
	public static String getPublicKeyHashFromFile(String file, String password) 
											throws FileNotFoundException, 
											CertificateException, NoSuchAlgorithmException, 
											KeyStoreException, IOException{
		KeyStore ks = CryptoTool.loadKeyStore(file, password);
		return CryptoTool.getPublicKeyHash(ks);
	}
	
	/**************
	 * SIGNATATURES 
	 **************/
	/**
	 * Signs any Serializable using the given keys
	 * 
	 * @param ks 		KeyStore 		= keys to perform signature with
	 * @param password 	String 			= password to unlock KeyStore
	 * @param obj		Serializable 	= object to sign
	 * @return SignedObject 			= signed object from Java
	 * @throws UnrecoverableKeyException
	 * @throws NoSuchAlgorithmException
	 * @throws KeyStoreException
	 * @throws SignatureException
	 * @throws InvalidKeyException
	 * @throws IOException
	 */
	public static SignedObject sign(KeyStore ks, String password, Serializable obj) 
								throws UnrecoverableKeyException, NoSuchAlgorithmException,
								KeyStoreException, SignatureException, InvalidKeyException,
								IOException{
		PrivateKey signingKey = (PrivateKey) ks.getKey(WhanauDHTConstants.CRYPTO_ALIAS,
														password.toCharArray());
		Signature signingEngine = Signature.getInstance(WhanauDHTConstants.CRYPTO_SIG_ALG,
													WhanauDHTConstants.CRYPTO_PROVIDER);
		SignedObject result = new SignedObject(obj, signingKey, signingEngine);
		return result;
	}
	
	/**
	 * Verifies that the owner of this public key truly signed the object
	 * 
	 * @param signedObj SignedObject 	= object with signature to verify
	 * @param pubKey 	PublicKey 		= the signer's public key
	 * @return boolean 					= true if verified, false otherwise
	 * @throws IOException
	 * @throws ClassNotFoundException
	 * @throws NoSuchAlgorithmException
	 * @throws InvalidKeySpecException
	 * @throws InvalidKeyException
	 * @throws SignatureException
	 */
	public static boolean verifySignature(SignedObject signedObj, PublicKey pubKey) 
											throws IOException, ClassNotFoundException, 
											NoSuchAlgorithmException, InvalidKeySpecException, 
											InvalidKeyException, SignatureException {
		Signature verificationEngine = Signature.getInstance(
												WhanauDHTConstants.CRYPTO_SIG_ALG, 
												WhanauDHTConstants.CRYPTO_PROVIDER);
		if (! signedObj.verify(pubKey, verificationEngine)) {
			return false;
		}
		return true;
	}
	
	/**
	 * Takes an encoded public key (byte[]) and converts it back into a PublicKey object
	 * 
	 * @param encodedKey byte[] = encoded public key
	 * @return PublicKey 		= Java object with public key
	 * @throws InvalidKeySpecException
	 * @throws NoSuchAlgorithmException
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public static PublicKey decodePublicKey(byte[] encodedKey) 
									throws InvalidKeySpecException, NoSuchAlgorithmException, 
									IOException, ClassNotFoundException {
		X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(encodedKey);
        KeyFactory keyFactory = KeyFactory.getInstance(WhanauDHTConstants.CRYPTO_KEY_ALG,
        												WhanauDHTConstants.CRYPTO_PROVIDER);
        PublicKey publicKey = keyFactory.generatePublic(pubKeySpec);
        return publicKey;
	}
	
	/**************
	 * SSL MANAGERS 
	 **************/
	/**
	 * Creates a Java TrustManager that trusts everyone.
	 * This bypasses the verification of certificate chains
	 * WhanauDHT only needs the SSL handshake
	 * 
	 * @return TrustManager[] = all-trusting manager
	 */
	public static TrustManager[] getAllTrustingManager() {
		TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
			public java.security.cert.X509Certificate[] getAcceptedIssuers() 
											{return new java.security.cert.X509Certificate[0];}
			public void checkClientTrusted(java.security.cert.X509Certificate[] certs, 
											String authType) {}
			public void checkServerTrusted(java.security.cert.X509Certificate[] certs, 
											String authType) {}
											}};
		return trustAllCerts;
	}
	
	/******************
	 * ENCODING SCHEMES 
	 ******************/
	/**
	 * Converts a byte array into its hexadecimal representation, stored in a String
	 * 
	 * @param data byte[] 	= data to convert
	 * @return String 		= string containing hex representation
	 */
	private static String convertToHex(byte[] data) {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < data.length; i++) {
        	int halfbyte = (data[i] >>> 4) & 0x0F;
        	int two_halfs = 0;
        	do {
	            if ((0 <= halfbyte) && (halfbyte <= 9))
	                buf.append((char) ('0' + halfbyte));
	            else
	            	buf.append((char) ('a' + (halfbyte - 10)));
	            halfbyte = data[i] & 0x0F;
        	} while(two_halfs++ < 1);
        }
        return buf.toString();
    }
	
	/****************
	 * HASH FUNCTIONS
	 ****************/
	/**
	 * SHA1 hash function
	 * 
	 * @param data byte[] 	= data to hash
	 * @return byte[]		= SHA1 hash of data\
	 * @throws NoSuchAlgorithmException = if "SHA-1" is not supported as a message digest
	 * @throws UnsupportedEncodingException = encoding failure
	 */
	public static byte[] SHA1(byte[] data) 
							throws NoSuchAlgorithmException, UnsupportedEncodingException {
		MessageDigest md;
		md = MessageDigest.getInstance("SHA-1");
		byte[] sha1hash = new byte[40];
		md.update(data, 0, data.length);
		sha1hash = md.digest();
		return sha1hash;
	}
	
	/**
	 * Performs a SHA1 hash on the text and returns the hex representation
	 * @param text String 	= text information to hash
	 * @return String 		= SHA1 hash of text, encoded in a hex string
	 * @throws NoSuchAlgorithmException
	 * @throws UnsupportedEncodingException
	 */
	public static String SHA1toHex(String text) 
							throws NoSuchAlgorithmException, UnsupportedEncodingException {
		return SHA1toHex(text.getBytes("iso-8859-1"));
    }
	
	/**
	 * Performs a SHA1 hash on binary data and returns the hex representation
	 *
	 * @param data byte[] 	= data to hash
	 * @return String 		= hex representation of its SHA1 hash
	 * @throws NoSuchAlgorithmException
	 * @throws UnsupportedEncodingException
	 */
	public static String SHA1toHex(byte[] data) 
							throws NoSuchAlgorithmException, UnsupportedEncodingException {
		return convertToHex(CryptoTool.SHA1(data));
	}
	
	/**
	 * Performs a SHA1 hash on the text and returns it in Base64 encoding
	 * 
	 * @param text String 	= text information to hash
	 * @return String 		= Base64 encoded SHA1-hash
	 * @throws NoSuchAlgorithmException
	 * @throws UnsupportedEncodingException
	 * @throws IOException
	 */
	public static String SHA1toBase64(String text) 
							throws NoSuchAlgorithmException, UnsupportedEncodingException,
									IOException  {
		return SHA1toBase64(text.getBytes("iso-8859-1"));
    }
	
	/**
	 * Performs a SHA1 hash on binary data and returns it in Base64 encoding
	 * 
	 * @param data byte[] 	= data to hash
	 * @return String 		= Base64 encoded SHA1-hash
	 * @throws NoSuchAlgorithmException
	 * @throws UnsupportedEncodingException
	 * @throws IOException
	 */
	public static String SHA1toBase64(byte[] data) 
							throws NoSuchAlgorithmException, UnsupportedEncodingException, 
							IOException {
		return Base64.encodeBytes(CryptoTool.SHA1(data));
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		int test = 2;
		String password = "password";
		try {
			//Test signing and verifying digital signature code
			if (test == 0) {
				KeyStore ks = CryptoTool.generateKeyStore(password);
				String message = "Test Message";
				SignedObject signedObj = CryptoTool.sign(ks, password, message);
				if (CryptoTool.verifySignature(signedObj, 
						CryptoTool.decodePublicKey(CryptoTool.getPublicKey(ks)))) {
					System.out.println("Signature Good");
				} else {
					System.out.println("Signature Bad");
				}
				System.out.println("Message="+signedObj.getObject().toString());
			//Test the hashing functions
			} else if (test == 1) {
				//SHA1 to Hex Tests
				String plain = "TEST";
				String sha = SHA1toHex(plain);
				int len = sha.length();
				System.out.println(plain+"="+sha+", len="+len);
				plain = "abcdefghijklmnopqrstuvwxyz";
				sha = SHA1toHex(plain);
				len = sha.length();
				System.out.println(plain+"="+sha+", len="+len);
				//SHA1 to Base64 Tests
				plain = "TEST";
				sha = SHA1toBase64(plain);
				len = sha.length();
				System.out.println(plain+"="+sha+", len="+len);
				plain = "abcdefghijklmnopqrstuvwxyz";
				sha = SHA1toBase64(plain);
				len = sha.length();
				System.out.println(plain+"="+sha+", len="+len);
			} else if (test == 2) {
				System.out.println(CryptoTool.getPublicKeyHashFromFile("lib/keys/0.jks", password));
				System.out.println(CryptoTool.getPublicKeyHashFromFile("lib/keys/1.jks", password));
				System.out.println(CryptoTool.getPublicKeyHashFromFile("lib/keys/2.jks", password));
				System.out.println(CryptoTool.getPublicKeyHashFromFile("lib/keys/3.jks", password));
				System.out.println(CryptoTool.getPublicKeyHashFromFile("lib/keys/4.jks", password));
				System.out.println(CryptoTool.getPublicKeyHashFromFile("lib/keys/5.jks", password));
				System.out.println(CryptoTool.getPublicKeyHashFromFile("lib/keys/6.jks", password));
				System.out.println(CryptoTool.getPublicKeyHashFromFile("lib/keys/7.jks", password));
				System.out.println(CryptoTool.getPublicKeyHashFromFile("lib/keys/8.jks", password));
				System.out.println(CryptoTool.getPublicKeyHashFromFile("lib/keys/9.jks", password));			
			} else if (test == 3) {
				/////////////////////////////////
				//Generate Keys
				KeyStore keys;
				int start = 0;
				int numKeys = 10;
				String directory="/home/developer/Desktop/keys/";
				for (int i=start;i<numKeys;i++){
					System.out.println("Generating Keystore "+i);
					keys = CryptoTool.generateKeyStore(password);
					CryptoTool.saveKeyStore(keys, directory+i+".jks", password);
				}
				System.out.println("Done");
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
}