package com.example.libreria.service;

import com.example.libreria.dto.*;
import com.example.libreria.model.Book;
import com.example.libreria.model.Reservation;
import com.example.libreria.model.User;
import com.example.libreria.repository.BookRepository;
import com.example.libreria.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {
    
    private static final BigDecimal LATE_FEE_PERCENTAGE = new BigDecimal("0.15"); // 15% por día
    
    private final ReservationRepository reservationRepository;
    private final BookRepository bookRepository;
    private final BookService bookService;
    private final UserService userService;
    private final ModelMapper modelMapper;

    @Transactional
    public ReservationResponseDTO createReservation(ReservationRequestDTO requestDTO) {

        // TODO: Implementar la creación de una reserva
        // Validar que el usuario existe
        UserResponseDTO user = userService.getUserById(requestDTO.getUserId());

        // Validar que el libro existe y está disponible
        BookResponseDTO book = bookService.getBookByExternalId(requestDTO.getBookExternalId());

        if (book.getStockQuantity() <= 0) {
            throw new RuntimeException("No esta disponible");
        }

        // Crear la reserva

        Reservation reservation = new Reservation();

        reservation.setUser(modelMapper.map(user, User.class));
        reservation.setBook(modelMapper.map(book, Book.class));
        reservation.setRentalDays(requestDTO.getRentalDays());
        reservation.setStartDate(requestDTO.getStartDate());
        reservation.setStatus(Reservation.ReservationStatus.ACTIVE);
        reservation.setDailyRate(book.getPrice());
        reservation.setTotalFee(calculateTotalFee(book.getPrice(),requestDTO.getRentalDays()));

        // Reducir la cantidad disponible
        bookService.decreaseAvailableQuantity(requestDTO.getBookExternalId());

        return convertToDTO(reservationRepository.save(reservation));
    }
    
    @Transactional
    public ReservationResponseDTO returnBook(Long reservationId, ReturnBookRequestDTO returnRequest) {

        // TODO: Implementar la devolución de un libro
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada con ID: " + reservationId));
        
        if (reservation.getStatus() != Reservation.ReservationStatus.ACTIVE) {
            throw new RuntimeException("La reserva ya fue devuelta");
        }
        
        LocalDate returnDate = returnRequest.getReturnDate();
        reservation.setActualReturnDate(returnDate);
        
        // Calcular tarifa por demora si hay retraso
        List<ReservationResponseDTO> reservasDemoradas = getOverdueReservations();

        BigDecimal tarifaDemora = BigDecimal.ZERO;

        if (reservasDemoradas.stream().filter(f -> f.getId() ==  reservation.getId()).findFirst().isPresent()){

            LocalDate fechaHoy = reservation.getActualReturnDate();
            LocalDate fechaDeDevolucion = reservation.getExpectedReturnDate();

            Long diasDeDiferencia = ChronoUnit.DAYS.between(fechaDeDevolucion, fechaHoy);

            reservation.setLateFee(calculateLateFee(
                    reservation.getBook().getPrice(),
                    diasDeDiferencia
            ));
        }
        
        // Aumentar la cantidad disponible
        bookService.increaseAvailableQuantity(reservation.getBook().getExternalId());
        return convertToDTO(reservationRepository.save(reservation));
    }
    
    @Transactional(readOnly = true)
    public ReservationResponseDTO getReservationById(Long id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reserva no encontrada con ID: " + id));
        return convertToDTO(reservation);
    }
    
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getAllReservations() {
        return reservationRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getReservationsByUserId(Long userId) {
        return reservationRepository.findByUserId(userId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getActiveReservations() {
        return reservationRepository.findByStatus(Reservation.ReservationStatus.ACTIVE).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getOverdueReservations() {
        return reservationRepository.findOverdueReservations().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    private BigDecimal calculateTotalFee(BigDecimal dailyRate, Integer rentalDays) {
        // TODO: Implementar el cálculo del total de la reserva
        BigDecimal totalReserva = new BigDecimal("0.00");

        totalReserva = dailyRate.multiply(new BigDecimal(rentalDays));

        return totalReserva;
    }
    
    private BigDecimal calculateLateFee(BigDecimal bookPrice, long daysLate) {
        // 15% del precio del libro por cada día de demora
        // TODO: Implementar el cálculo de la multa por demora

        BigDecimal multa = new BigDecimal("0.00");

        multa = bookPrice
                .multiply(LATE_FEE_PERCENTAGE)
                .multiply(BigDecimal.valueOf(daysLate))
                .setScale(2, RoundingMode.UP);

        return multa;
    }
    
    private ReservationResponseDTO convertToDTO(Reservation reservation) {
        ReservationResponseDTO dto = new ReservationResponseDTO();
        dto.setId(reservation.getId());
        dto.setUserId(reservation.getUser().getId());
        dto.setUserName(reservation.getUser().getName());
        dto.setBookExternalId(reservation.getBook().getExternalId());
        dto.setBookTitle(reservation.getBook().getTitle());
        dto.setRentalDays(reservation.getRentalDays());
        dto.setStartDate(reservation.getStartDate());
        dto.setExpectedReturnDate(reservation.getExpectedReturnDate());
        dto.setActualReturnDate(reservation.getActualReturnDate());
        dto.setDailyRate(reservation.getDailyRate());
        dto.setTotalFee(reservation.getTotalFee());
        dto.setLateFee(reservation.getLateFee());
        dto.setStatus(reservation.getStatus());
        dto.setCreatedAt(reservation.getCreatedAt());
        return dto;
    }
}

