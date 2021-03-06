package com.ibm.pross.server.messages;

import java.io.Serializable;

/**
 * A relayed message is a signed message wrapped with a signature generated by
 * the entity relaying the message. The relaying entity is the immediate sender
 * of the message, and not necessarily the originator of the message (which is
 * given by the sender id of the internal signed message).
 */
public class RelayedMessage implements Serializable {

	private static final long serialVersionUID = -2438354315804580247L;

	private final int relayerId;
	private final boolean isAcknowledgement;
	private final SignedMessage signedMessage;

	/**
	 * This class represents a relayed message. It wraps a SignedMessage produced by
	 * a message originator. This message is then signed and sent by a message
	 * relayer. This is useful in broadcast operationrs to forward a broadcast
	 * message, and also to prove receipt of a message when generating a message
	 * acknowledgement.
	 * 
	 * @param relayerId
	 *            The ID of the relaying party and the producer of the
	 *            MessageSignature for this message
	 *            
	 * @param isAcknowledgement
	 *            True when this is a reply in acknowledgement of a received
	 *            message. In general this means no reply should be given by the
	 *            recipient to prevent infinite back and forth responses. Instead
	 *            the sender, upon receipt of an acknowledgement, can cease repeated
	 *            sending of a message.
	 *            
	 * @param signedMessage
	 *            The SignedMessage which is wrapped and being relayed (produced and
	 *            signed by the originator)
	 */
	public RelayedMessage(final int relayerId, boolean isAcknowledgement, SignedMessage signedMessage) {
		super();
		this.relayerId = relayerId;
		this.isAcknowledgement = isAcknowledgement;
		this.signedMessage = signedMessage;
	}

	public int getRelayerId() {
		return relayerId;
	}

	public boolean isAcknowledgement() {
		return isAcknowledgement;
	}

	public SignedMessage getSignedMessage() {
		return signedMessage;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (isAcknowledgement ? 1231 : 1237);
		result = prime * result + relayerId;
		result = prime * result + ((signedMessage == null) ? 0 : signedMessage.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RelayedMessage other = (RelayedMessage) obj;
		if (isAcknowledgement != other.isAcknowledgement)
			return false;
		if (relayerId != other.relayerId)
			return false;
		if (signedMessage == null) {
			if (other.signedMessage != null)
				return false;
		} else if (!signedMessage.equals(other.signedMessage))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "RelayedMessage [relayerId=" + relayerId + ", isAcknowledgement=" + isAcknowledgement
				+ ", signedMessage=" + signedMessage + "]";
	}



	
	
}
